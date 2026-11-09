package com.bysellens.sales;

import com.bysellens.common.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SalesController {
  public record Line(
      @NotNull @Positive Long productoId, @NotNull @Positive @Max(1000000) Integer cantidad) {}

  public record Input(
      @NotNull @Positive Long clienteId,
      @NotNull @Pattern(regexp = "EFECTIVO|TARJETA|TRANSFERENCIA|NEQUI|DAVIPLATA")
          String metodoPago,
      @NotEmpty @Size(max = 100) List<@Valid Line> detalles) {}

  private final SalesService service;

  public SalesController(SalesService s) {
    service = s;
  }

  @GetMapping("/api/ventas")
  public List<Venta> list() {
    return service.list();
  }

  @GetMapping("/api/ventas/{id}")
  public Venta get(@PathVariable long id) {
    return service.get(id);
  }

  @PostMapping("/api/ventas")
  @ResponseStatus(HttpStatus.CREATED)
  public Venta create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Input input) {
    var v = service.create(key, input);
    if (v.estado.equals("RECHAZADA"))
      throw new DomainException(409, "Existencias insuficientes; venta rechazada");
    return v;
  }
}
