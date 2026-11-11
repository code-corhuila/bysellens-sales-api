package com.bysellens.sales;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "JWT_SECRET=test-secret-at-least-thirty-two-bytes-long",
      "outbox.enabled=false"
    })
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RabbitSalesIntegrationTest {
  @Container
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container
  static GenericContainer<?> rabbit =
      new GenericContainer<>("rabbitmq:3.13-management")
          .withEnv("RABBITMQ_DEFAULT_USER", "test")
          .withEnv("RABBITMQ_DEFAULT_PASS", "test-password")
          .withExposedPorts(5672);

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> pg.getJdbcUrl() + "&currentSchema=sales");
    registry.add("spring.datasource.username", pg::getUsername);
    registry.add("spring.datasource.password", pg::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
    registry.add("spring.rabbitmq.username", () -> "test");
    registry.add("spring.rabbitmq.password", () -> "test-password");
  }

  @Autowired JdbcTemplate db;
  @Autowired RabbitTemplate rabbitTemplate;

  @Test
  void brokerResultConfirmsPendingSaleOnce() {
    Long id =
        db.queryForObject(
            "INSERT INTO ventas(cliente,fecha,metodo_pago,total,estado,clave,solicitud) "
                + "VALUES ('{}'::jsonb,now(),'EFECTIVO',10,'PENDIENTE','test-rabbit','{}') "
                + "RETURNING id",
            Long.class);
    String body =
        "{\"version\":1,\"eventId\":\"result-venta-"
            + id
            + "\",\"ventaId\":"
            + id
            + ",\"reservaId\":\"venta-"
            + id
            + "\",\"estado\":\"CONFIRMADA\"}";
    for (int i = 0; i < 2; i++) {
      rabbitTemplate.send(
          MessagingConfig.EXCHANGE,
          "result",
          MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build());
    }

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    String state = "";
    while (System.nanoTime() < deadline) {
      state = db.queryForObject("SELECT estado FROM ventas WHERE id=?", String.class, id);
      if ("CONFIRMADA".equals(state)) break;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    assertEquals("CONFIRMADA", state);
  }
}
