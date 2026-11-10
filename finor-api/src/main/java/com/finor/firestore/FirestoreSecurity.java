package com.finor.firestore;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("firestore-legacy-auth")
public class FirestoreSecurity {
  @Bean
  SecretKeySpec jwtKey(@Value("${JWT_SECRET}") String key) {
    if (key.length() < 64)
      throw new IllegalStateException("JWT_SECRET deve ter pelo menos 64 caracteres.");
    return new SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key, FirestoreAuth auth) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("finor"),
            token -> {
              try {
                UUIDCheck.check(token.getSubject());
                UUIDCheck.check(token.getClaimAsString("sid"));
                if (auth.active(token.getSubject(), token.getClaimAsString("sid")))
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
  SecurityFilterChain chain(
      HttpSecurity http, @Value("${finor.require-https:false}") boolean secure) throws Exception {
    if (secure) http.requiresChannel(c -> c.anyRequest().requiresSecure());
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }

  private static class UUIDCheck {
    static void check(String value) {
      java.util.UUID.fromString(value);
    }
  }
}

