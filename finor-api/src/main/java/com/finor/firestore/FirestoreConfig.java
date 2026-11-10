package com.finor.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile("firestore")
public class FirestoreConfig {
  @Bean(destroyMethod = "close")
  Firestore firestore(
      @Value("${FIREBASE_PROJECT_ID}") String project,
      @Value("${FIREBASE_DATABASE_ID:(default)}") String database,
      Environment env)
      throws Exception {
    String emulator = System.getenv("FIRESTORE_EMULATOR_HOST");
    var builder = FirestoreOptions.newBuilder().setProjectId(project).setDatabaseId(database);
    if (emulator != null && !emulator.isBlank()) {
      if (env.matchesProfiles("prod") || !project.startsWith("demo-"))
        throw new IllegalStateException(
            "Emulador permitido apenas com projeto demo- e fora de produção.");
      if (!emulator.matches("(127\\.0\\.0\\.1|localhost):[0-9]{2,5}"))
        throw new IllegalStateException("O emulador deve usar uma porta de loopback local.");
      String host = emulator.replace("localhost:", "127.0.0.1:");
      // Public emulator-only Admin marker; this is not a production service credential.
      var localCredentials =
          com.google.auth.oauth2.OAuth2Credentials.create(
              new com.google.auth.oauth2.AccessToken(
                  "owner", java.util.Date.from(java.time.Instant.now().plusSeconds(86400))));
      builder.setHost(host).setEmulatorHost(host).setCredentials(localCredentials);
      builder.setChannelProvider(
          com.google.api.gax.grpc.InstantiatingGrpcChannelProvider.newBuilder()
              .setEndpoint(host)
              .setChannelConfigurator(channel -> channel.usePlaintext())
              .build());
    } else {
      if (project.startsWith("demo-"))
        throw new IllegalStateException("Projeto demo requer FIRESTORE_EMULATOR_HOST.");
      builder.setCredentials(GoogleCredentials.getApplicationDefault());
    }
    return builder.build().getService();
  }
}
