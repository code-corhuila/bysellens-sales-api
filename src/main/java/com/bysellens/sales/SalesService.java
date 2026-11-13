package com.bysellens.sales;

import com.bysellens.common.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class SalesService {
  private final JdbcTemplate jdbc;
  private final VentaRepository repo;
  private final Remote remote;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final String customer, product, inventory;

  public SalesService(
      VentaRepository repo,
      Remote remote,
      ObjectMapper mapper,
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      @Value("${customer.url}") String customer,
      @Value("${product.url}") String product,
      @Value("${inventory.url}") String inventory) {
    this.repo = repo;
    this.remote = remote;
    this.mapper = mapper;
    tx = new TransactionTemplate(manager);
    this.customer = customer;
    this.product = product;
    this.inventory = inventory;
  }

  JsonNode lookup(String url) {
    try {
      return remote.get(url, JsonNode.class);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == 404)
        throw new DomainException(404, "Cliente o producto no encontrado");
      throw e;
    }
  }

  public List<Venta> list() {
    return repo.findByEstadoOrderByIdDesc("CONFIRMADA");
  }

  public Venta get(long id) {
    return repo.findById(id).orElseThrow(DomainException::missing);
  }

  String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Venta create(String key, SalesController.Input input) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new DomainException(400, "Idempotency-Key requerido (maximo 100 caracteres)");
    if (input.detalles().stream().map(SalesController.Line::productoId).distinct().count()
        != input.detalles().size()) throw new DomainException(400, "Productos repetidos");
    String payload = json(input);
    var existing = repo.findByClave(key);
    Venta sale;
    if (existing.isPresent()) sale = existing.get();
    else {
      var c = lookup(customer + "/internal/clientes/" + input.clienteId());
      if (!c.path("activo").asBoolean()) throw new DomainException(409, "Cliente inactivo");
      sale = new Venta();
      sale.clave = key;
      sale.solicitud = payload;
      sale.cliente = c;
      sale.metodoPago = input.metodoPago();
      sale.total = BigDecimal.ZERO;
      for (var line : input.detalles()) {
        var p = lookup(product + "/internal/productos/" + line.productoId());
        if (!p.path("activo").asBoolean()) throw new DomainException(409, "Producto inactivo");
        var d = new Detalle();
        d.producto = p;
        d.cantidad = line.cantidad();
        d.precio = p.path("precioVenta").decimalValue();
        d.subtotal = d.precio.multiply(BigDecimal.valueOf(d.cantidad));
        sale.detalles.add(d);
        sale.total = sale.total.add(d.subtotal);
      }
      if (sale.total.compareTo(new BigDecimal("99999999999999.99")) > 0)
        throw new DomainException(400, "Total fuera de rango");
      final Venta candidate = sale;
      try {
        sale = tx.execute(s -> repo.saveAndFlush(candidate));
      } catch (DataIntegrityViolationException e) {
        sale = repo.findByClave(key).orElseThrow(() -> e);
      }
    }
    if (!sale.solicitud.equals(payload))
      throw new DomainException(409, "Idempotency-Key ya utilizado con otra solicitud");
    return process(sale.id);
  }

  public Venta process(long id) {
    return tx.execute(transaction -> {
      var sale = repo.lock(id).orElseThrow(DomainException::missing);
      if (sale.estado.equals("CONFIRMADA")) return sale;
      if (sale.estado.equals("RECHAZADA"))
        throw new DomainException(409, "Venta rechazada por inventario");
      String reservation = "venta-" + sale.id;
      var lines =
          sale.detalles.stream()
              .map(d ->
                  Map.of(
                      "productoId", d.producto.path("id").asLong(), "cantidad", (long) d.cantidad))
              .toList();
      try {
        var result = remote.post(
            inventory + "/internal/reservas/" + reservation,
            Map.of("detalles", lines),
            JsonNode.class);
        String status = result.path("estado").asText();
        if ("CONFIRMADA".equals(status)) {
          sale.estado = "CONFIRMADA";
          return repo.saveAndFlush(sale);
        }
        if (!"RESERVADA".equals(status))
          throw new IllegalStateException("Unexpected inventory reservation state");
        String eventId = "confirm-venta-" + sale.id;
        String event =
            json(
                Map.of(
                    "version", 1,
                    "eventId", eventId,
                    "ventaId", sale.id,
                    "reservaId", reservation,
                    "detalles", lines,
                    "fecha", sale.fecha.toString()));
        jdbc.update(
            "INSERT INTO rabbit_outbox(id,routing_key,payload) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING",
            eventId,
            "confirm",
            event);
        return sale;
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 409 || e.getStatusCode().value() == 404) {
          try {
            var status =
                remote
                    .get(inventory + "/internal/reservas/" + reservation, JsonNode.class)
                    .path("estado")
                    .asText();
            if (status.equals("CONFIRMADA")) {
              sale.estado = "CONFIRMADA";
              return repo.saveAndFlush(sale);
            }
            if (status.equals("RESERVADA"))
              throw new IllegalStateException("Active reservation requires reconciliation");
          } catch (HttpClientErrorException missing) {
            if (missing.getStatusCode().value() != 404) throw missing;
          }
          sale.estado = "RECHAZADA";
          return repo.saveAndFlush(sale);
        }
        throw e;
      }
    });
  }

  @Scheduled(fixedDelayString = "30000", initialDelayString = "30000")
  public void recover() {
    for (var v : repo.findByEstadoOrderByIdDesc("PENDIENTE")) {
      try {
        process(v.id);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Venta {} pendiente de reconciliacion", v.id);
      }
    }
  }
}
