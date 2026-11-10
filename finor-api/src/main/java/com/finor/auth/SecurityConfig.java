package com.finor.auth;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!firestore")
@Configuration
public class SecurityConfig {
  @Bean
  SecretKeySpec jwtKey(@Value("${JWT_SECRET}") String secret) {
    if (secret.length() < 64)
      throw new IllegalStateException("JWT_SECRET deve ter pelo menos 64 caracteres aleatórios.");
    return new SecretKeySpec(
        secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key, org.springframework.jdbc.core.JdbcTemplate db) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("finor"),
            token -> {
              try {
                var user = java.util.UUID.fromString(token.getSubject());
                var family = java.util.UUID.fromString(token.getClaimAsString("sid"));
                if (db.queryForObject(
                        "SELECT COUNT(*) FROM refresh_token WHERE user_id=? AND family_id=? AND"
                            + " revoked=false AND expires_at>now()",
                        Integer.class,
                        user,
                        family)
                    > 0)
                  return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
                      .success();
              } catch (IllegalArgumentException ignored) {
              }
              return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                  new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
            }));
    return decoder;
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
  }

  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http, @Value("${finor.require-https:false}") boolean secure) throws Exception {
    if (secure) http.requiresChannel(c -> c.anyRequest().requiresSecure());
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers(
                        "/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }
}


