package com.bysellens.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ventas")
public class Venta {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  public JsonNode cliente;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @JoinColumn(name = "venta_id", nullable = false)
  @OrderBy("id asc")
  public List<Detalle> detalles = new ArrayList<>();

  public Instant fecha = Instant.now();
  public String metodoPago;

  @Column(precision = 16, scale = 2)
  public BigDecimal total;

  public String estado = "PENDIENTE";

  @JsonIgnore
  @Column(unique = true)
  public String clave;

  @JsonIgnore
  @Column(columnDefinition = "text")
  public String solicitud;
}
