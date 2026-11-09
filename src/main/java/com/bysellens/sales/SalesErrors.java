package com.bysellens.sales;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SalesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SalesErrors {
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<?> missingHeader(MissingRequestHeaderException exception) {
    String requestId = MDC.get("requestId");
    LoggerFactory.getLogger(SalesErrors.class)
        .warn(
            "Solicitud de venta rechazada: status=400 causa=cabecera_obligatoria requestId={}",
            requestId);
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error", "Solicitud invalida",
                "mensaje",
                    "Falta la cabecera obligatoria Idempotency-Key. Reintenta desde el formulario"
                        + " actualizado.",
                "requestId", requestId == null ? "" : requestId));
  }
}
