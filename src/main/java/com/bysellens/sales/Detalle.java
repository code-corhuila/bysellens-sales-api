package com.bysellens.sales;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "detalles")
public class Detalle {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public int cantidad;

  @Column(precision = 14, scale = 2)
  public BigDecimal precio;

  @Column(precision = 16, scale = 2)
  public BigDecimal subtotal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  public JsonNode producto;
}
