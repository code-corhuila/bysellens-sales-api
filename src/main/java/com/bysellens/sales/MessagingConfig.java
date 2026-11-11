package com.bysellens.sales;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration
class MessagingConfig {
  static final String EXCHANGE = "bysellens.sales.v1";
  static final String DEAD = "bysellens.sales.dlx";

  @Bean
  DirectExchange salesExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  DirectExchange deadExchange() {
    return new DirectExchange(DEAD, true, false);
  }

  @Bean
  Queue confirmQueue() {
    return QueueBuilder.durable("bysellens.inventory.confirm.v1")
        .deadLetterExchange(DEAD)
        .deadLetterRoutingKey("confirm.failed")
        .build();
  }

  @Bean
  Queue resultQueue() {
    return QueueBuilder.durable("bysellens.sales.result.v1")
        .deadLetterExchange(DEAD)
        .deadLetterRoutingKey("result.failed")
        .build();
  }

  @Bean
  Queue confirmDead() {
    return QueueBuilder.durable("bysellens.inventory.confirm.dlq.v1").build();
  }

  @Bean
  Queue resultDead() {
    return QueueBuilder.durable("bysellens.sales.result.dlq.v1").build();
  }

  @Bean
  Binding confirmBinding() {
    return BindingBuilder.bind(confirmQueue()).to(salesExchange()).with("confirm");
  }

  @Bean
  Binding resultBinding() {
    return BindingBuilder.bind(resultQueue()).to(salesExchange()).with("result");
  }

  @Bean
  Binding confirmDeadBinding() {
    return BindingBuilder.bind(confirmDead()).to(deadExchange()).with("confirm.failed");
  }

  @Bean
  Binding resultDeadBinding() {
    return BindingBuilder.bind(resultDead()).to(deadExchange()).with("result.failed");
  }
}

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate db;
  private final RabbitTemplate rabbit;

  OutboxPublisher(JdbcTemplate db, RabbitTemplate rabbit) {
    this.db = db;
    this.rabbit = rabbit;
    this.rabbit.setMandatory(true);
  }

  @Scheduled(fixedDelayString = "${outbox.delay:2000}")
  @Transactional
  public void publish() {
    var rows =
        db.queryForList(
            "SELECT id,routing_key,payload FROM rabbit_outbox "
                + "WHERE sent_at IS NULL ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      try {
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        var message =
            MessageBuilder.withBody(
                    ((String) row.get("payload")).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbit.send(
            MessagingConfig.EXCHANGE,
            (String) row.get("routing_key"),
            message,
            correlation);
        var result = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!result.isAck() || correlation.getReturned() != null)
          throw new IllegalStateException("Message was not acknowledged or routed");
        db.update("UPDATE rabbit_outbox SET sent_at=now() WHERE id=?", row.get("id"));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Outbox message publication interrupted: {}", row.get("id"), e);
        break;
      } catch (Exception e) {
        log.warn("Outbox message publication pending: {}", row.get("id"), e);
        break;
      }
    }
  }
}
