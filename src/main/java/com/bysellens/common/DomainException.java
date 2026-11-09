package com.bysellens.common;

public class DomainException extends RuntimeException {
  public final int status;

  public DomainException(int status, String message) {
    super(message);
    this.status = status;
  }

  public static DomainException missing() {
    return new DomainException(404, "Recurso no encontrado");
  }
}
