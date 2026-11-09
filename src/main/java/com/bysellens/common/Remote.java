package com.bysellens.common;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Remote {
  private final RestClient client;
  private final Tokens tokens;

  public Remote(Tokens tokens) {
    this.tokens = tokens;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(10));
    client = RestClient.builder().requestFactory(factory).build();
  }

  private String trace() {
    String id = org.slf4j.MDC.get("requestId");
    return id == null ? java.util.UUID.randomUUID().toString() : id;
  }

  public <T> T get(String url, Class<T> type) {
    return client
        .get()
        .uri(url)
        .headers(h -> {
          h.setBearerAuth(tokens.issue("internal", "SERVICE"));
          h.set("X-Request-ID", trace());
        })
        .retrieve()
        .body(type);
  }

  public <T> T post(String url, Object body, Class<T> type) {
    return client
        .post()
        .uri(url)
        .headers(h -> {
          h.setBearerAuth(tokens.issue("internal", "SERVICE"));
          h.set("X-Request-ID", trace());
        })
        .body(body)
        .retrieve()
        .body(type);
  }
}
