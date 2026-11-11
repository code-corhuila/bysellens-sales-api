package com.bysellens.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SalesMessages {
  private final VentaRepository ventas;
  private final ObjectMapper mapper;

  SalesMessages(VentaRepository ventas, ObjectMapper mapper) {
    this.ventas = ventas;
    this.mapper = mapper;
  }

  @RabbitListener(queues = "bysellens.sales.result.v1")
  @Transactional
  public void result(byte[] bytes) throws Exception {
    JsonNode event = mapper.readTree(bytes);
    long id = event.path("ventaId").asLong(-1);
    String status = event.path("estado").asText();
    if (event.path("version").asInt() != 1
        || id <= 0
        || !event.path("eventId").asText().equals("result-venta-" + id)
        || !event.path("reservaId").asText().equals("venta-" + id)
        || (!status.equals("CONFIRMADA") && !status.equals("CANCELADA"))) {
      throw new IllegalArgumentException("Invalid inventory result event");
    }
    var sale = ventas.lock(id).orElseThrow(() -> new IllegalStateException("Sale not found"));
    String target = status.equals("CONFIRMADA") ? "CONFIRMADA" : "RECHAZADA";
    if (sale.estado.equals(target)) return;
    if (!sale.estado.equals("PENDIENTE"))
      throw new IllegalStateException("Contradictory sale result event");
    sale.estado = target;
    ventas.saveAndFlush(sale);
  }
}
