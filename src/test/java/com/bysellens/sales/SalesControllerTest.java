package com.bysellens.sales;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bysellens.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = SalesController.class,
    properties = {
      "JWT_SECRET=test-secret-at-least-thirty-two-bytes-long",
      "spring.jackson.deserialization.fail-on-unknown-properties=true",
      "spring.jackson.deserialization.accept-float-as-int=false"
    })
@Import({SecurityConfig.class, Tokens.class, Errors.class, TraceFilter.class, SalesErrors.class})
class SalesControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean SalesService service;
  static final String BODY =
      "{\"clienteId\":1,\"metodoPago\":\"EFECTIVO\",\"detalles\":[{\"productoId\":2,\"cantidad\":1}]}";

  @Test
  void missingIdempotencyHeaderIsExplicitAndDoesNotCreateSale() throws Exception {
    mvc.perform(
            post("/api/ventas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
    verifyNoInteractions(service);
  }

  @Test
  void validRequestReachesServiceWithKey() throws Exception {
    var sale = new Venta();
    sale.id = 1L;
    sale.estado = "CONFIRMADA";
    when(service.create(eq("request-1"), any())).thenReturn(sale);
    mvc.perform(
            post("/api/ventas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .header("Idempotency-Key", "request-1")
                .contentType("application/json")
                .content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1));
    verify(service).create(eq("request-1"), any());
  }

  @Test
  void invalidNullFractionalAndUnknownFieldsNeverCreateSale() throws Exception {
    for (String body :
        new String[] {
          BODY.replace("\"cantidad\":1", "\"cantidad\":0"),
          BODY.replace("\"cantidad\":1", "\"cantidad\":1.5"),
          BODY.replace("\"clienteId\":1", "\"clienteId\":null"),
          BODY.replace("\"clienteId\":1", "\"clienteId\":1,\"total\":0")
        }) {
      mvc.perform(
              post("/api/ventas")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                  .header("Idempotency-Key", "invalid")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void unauthenticatedAndUnprivilegedRequestsAreRejected() throws Exception {
    mvc.perform(post("/api/ventas").contentType("application/json").content(BODY))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/ventas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_READER")))
                .contentType("application/json")
                .content(BODY))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }
}
