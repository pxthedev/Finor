package com.finor.firestore.identity;

import com.google.auth.oauth2.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile("firestore & !firestore-legacy-auth")
public class FirebaseIdentityConfig {
  @Bean(destroyMethod = "delete")
  FirebaseApp firebaseIdentityApp(@Value("${FIREBASE_PROJECT_ID}") String project, Environment env)
      throws Exception {
    String emulator = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
    GoogleCredentials credentials;
    if (emulator != null && !emulator.isBlank()) {
      if (env.matchesProfiles("prod")
          || !project.startsWith("demo-")
          || !emulator.matches("(127[.]0[.]0[.]1|localhost):[0-9]{2,5}"))
        throw new IllegalStateException(
            "Auth Emulator permitido apenas em loopback, projeto demo e fora de produção.");
      credentials =
          GoogleCredentials.create(
              new AccessToken("owner", Date.from(Instant.now().plusSeconds(86400))));
    } else {
      if (project.startsWith("demo-"))
        throw new IllegalStateException("Projeto demo requer FIREBASE_AUTH_EMULATOR_HOST.");
      credentials = GoogleCredentials.getApplicationDefault();
    }
    return FirebaseApp.initializeApp(
        FirebaseOptions.builder().setProjectId(project).setCredentials(credentials).build(),
        "finor-auth");
  }

  @Bean
  FirebaseAuth firebaseAuth(FirebaseApp app) {
    return FirebaseAuth.getInstance(app);
  }
}
