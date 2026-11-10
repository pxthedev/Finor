package com.finor.firestore.identity;

import com.finor.firestore.FirebaseIdentity;
import com.google.firebase.auth.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("firestore & !firestore-legacy-auth")
public class FirebaseTokenSecurity {
  @Bean
  BCryptPasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtDecoder decoder(FirebaseIdentity identity) {
    return raw -> {
      try {
        FirebaseToken token = identity.verify(raw);
        var claims = new LinkedHashMap<String, Object>(token.getClaims());
        String owner = identity.owner(token);
        claims.put("sub", owner);
        claims.put("firebase_uid", token.getUid());
        return new Jwt(
            raw,
            Instant.ofEpochSecond(((Number) claims.get("iat")).longValue()),
            Instant.ofEpochSecond(((Number) claims.get("exp")).longValue()),
            Map.of("alg", "RS256"),
            claims);
      } catch (FirebaseAuthException | IllegalArgumentException e) {
        throw new BadJwtException("Firebase ID token inválido, expirado ou revogado.");
      }
    };
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
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }
}
