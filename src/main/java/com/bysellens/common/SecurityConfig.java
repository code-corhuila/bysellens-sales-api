package com.bysellens.common;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  SecretKeySpec signingKey(@Value("${JWT_SECRET}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("bysellens"));
    return decoder;
  }

  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, JwtDecoder decoder) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("rol");
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/api/v1/auth/login",
                    "/uploads/productos/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html")
                .permitAll()
                .requestMatchers("/internal/**")
                .hasRole("SERVICE")
                .anyRequest()
                .hasRole("ADMIN"))
        .oauth2ResourceServer(o ->
            o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((req, res, e) -> {
                  res.setStatus(401);
                  res.setContentType("application/json");
                  res.getWriter().write("{\"error\":\"Autenticacion requerida\"}");
                }))
        .exceptionHandling(e ->
            e.accessDeniedHandler((req, res, ex) -> {
              res.setStatus(403);
              res.setContentType("application/json");
              res.getWriter().write("{\"error\":\"Permisos insuficientes\"}");
            }))
        .build();
  }
}
