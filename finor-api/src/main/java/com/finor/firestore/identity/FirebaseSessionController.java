package com.finor.firestore.identity;

import com.finor.firestore.FirebaseIdentity;
import com.google.firebase.auth.*;
import java.time.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("firestore & !firestore-legacy-auth")
@RequestMapping("/api/v1/auth")
public class FirebaseSessionController {
  private final FirebaseIdentity identity;

  public FirebaseSessionController(FirebaseIdentity identity) {
    this.identity = identity;
  }

  @PostMapping("/bootstrap")
  public Object bootstrap(@AuthenticationPrincipal Jwt jwt) throws FirebaseAuthException {
    return identity.bootstrap(identity.verify(jwt.getTokenValue()), jwt.getSubject());
  }

  @PostMapping("/logout")
  public void logout(@AuthenticationPrincipal Jwt jwt) {
    identity.revoke(jwt.getClaimAsString("firebase_uid"));
  }

  public static void requireRecent(Jwt token) {
    Object value = token.getClaims().get("auth_time");
    if (!(value instanceof Number n)
        || Instant.ofEpochSecond(n.longValue()).isBefore(Instant.now().minusSeconds(300)))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Entre novamente para confirmar esta operação.");
  }
}
