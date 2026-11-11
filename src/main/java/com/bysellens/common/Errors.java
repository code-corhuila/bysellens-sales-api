package com.bysellens.common;

import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(DomainException.class)
  ResponseEntity<?> domain(DomainException e) {
    return response(e.status, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var errors = new TreeMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.put(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Validacion invalida", "errores", errors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.multipart.MaxUploadSizeExceededException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return response(400, "Solicitud invalida o campos no permitidos");
  }

  @ExceptionHandler(RestClientException.class)
  ResponseEntity<?> remote(Exception e) {
    return response(503, "Servicio dependiente no disponible");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<?> missing(Exception e) {
    return response(404, "Recurso no encontrado");
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<?> denied(Exception e) {
    return response(403, "Permisos insuficientes");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e) {
    return response(500, "No fue posible procesar la solicitud");
  }

  static ResponseEntity<?> response(int code, String message) {
    return ResponseEntity.status(code).body(Map.of("error", message, "mensaje", message));
  }
}
