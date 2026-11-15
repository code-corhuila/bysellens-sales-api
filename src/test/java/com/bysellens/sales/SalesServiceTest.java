package com.bysellens.sales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bysellens.common.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.ResourceAccessException;

class SalesServiceTest {
  @Test
  void confirmedSaleDoesNotDiscountAgain() {
    var repo = mock(VentaRepository.class);
    var remote = mock(Remote.class);
    var jdbc = mock(JdbcTemplate.class);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var sale = new Venta();
    sale.id = 1L;
    sale.estado = "CONFIRMADA";
    when(repo.lock(1L)).thenReturn(Optional.of(sale));
    var service =
        new SalesService(
            repo, remote, new ObjectMapper(), jdbc, manager, "customer", "product", "inventory");
    assertEquals("CONFIRMADA", service.process(1).estado);
    verifyNoInteractions(remote, jdbc);
  }

  @Test
  void uncertainInventoryCommitLeavesDurablePendingSale() {
    var repo = mock(VentaRepository.class);
    var remote = mock(Remote.class);
    var jdbc = mock(JdbcTemplate.class);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var sale = new Venta();
    sale.id = 2L;
    when(repo.lock(2L)).thenReturn(Optional.of(sale));
    when(remote.post(
            eq("inventory/internal/reservas/venta-2"), any(), eq(JsonNode.class)))
        .thenThrow(new ResourceAccessException("timeout"));
    var service =
        new SalesService(
            repo, remote, new ObjectMapper(), jdbc, manager, "customer", "product", "inventory");
    assertThrows(ResourceAccessException.class, () -> service.process(2));
    assertEquals("PENDIENTE", sale.estado);
    verify(manager).rollback(any());
  }

  @Test
  void reservationEnqueuesConfirmationAndLeavesSalePending() throws Exception {
    var repo = mock(VentaRepository.class);
    var remote = mock(Remote.class);
    var manager = mock(PlatformTransactionManager.class);
    var jdbc = mock(JdbcTemplate.class);
    var mapper = new ObjectMapper();
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var sale = new Venta();
    sale.id = 3L;
    var detail = new Detalle();
    detail.producto = mapper.readTree("{\"id\":12}");
    detail.cantidad = 2;
    sale.detalles.add(detail);
    when(repo.lock(3L)).thenReturn(Optional.of(sale));
    when(remote.post(
            eq("inventory/internal/reservas/venta-3"), any(), eq(JsonNode.class)))
        .thenReturn(mapper.readTree("{\"estado\":\"RESERVADA\"}"));
    var service =
        new SalesService(
            repo, remote, mapper, jdbc, manager, "customer", "product", "inventory");

    assertEquals("PENDIENTE", service.process(3).estado);
    verify(jdbc)
        .update(
            contains("INSERT INTO rabbit_outbox"),
            eq("confirm-venta-3"),
            eq("confirm"),
            anyString());
    verify(remote, never())
        .post(contains("/confirmar"), any(), eq(JsonNode.class));
  }
}
