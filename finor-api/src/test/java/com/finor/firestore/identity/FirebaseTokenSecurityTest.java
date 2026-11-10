package com.finor.firestore.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finor.firestore.FirebaseIdentity;
import com.google.firebase.auth.FirebaseToken;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FirebaseTokenSecurityTest {
  @Test
  void verifiedFirebaseIdentityMapsToFinancialOwner() throws Exception {
    var identity = mock(FirebaseIdentity.class);
    var token = mock(FirebaseToken.class);
    long now = Instant.now().getEpochSecond();
    when(identity.verify("firebase-id-token")).thenReturn(token);
    when(token.getClaims())
        .thenReturn(Map.of("sub", "google-user", "iat", now, "exp", now + 3600, "auth_time", now));
    when(token.getUid()).thenReturn("google-user");
    when(identity.owner(token)).thenReturn("financial-owner");
    var jwt = new FirebaseTokenSecurity().decoder(identity).decode("firebase-id-token");
    assertEquals("financial-owner", jwt.getSubject());
    assertEquals("google-user", jwt.getClaimAsString("firebase_uid"));
    verify(identity).verify("firebase-id-token");
  }

  @Test
  void deletionRequiresRecentReauthentication() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject("owner")
            .claim("auth_time", Instant.now().minusSeconds(600).getEpochSecond())
            .build();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> FirebaseSessionController.requireRecent(jwt));
  }
}
