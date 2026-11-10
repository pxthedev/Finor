package com.finor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finor.assistant.AssistantProvider;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Opt-in checks against real services. No database, mock provider, or financial writes. */
@EnabledIfEnvironmentVariable(named = "FINOR_LIVE_INTEGRATIONS", matches = "1")
class LiveIntegrationsTest {
  private String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new AssertionError("Variável obrigatória ausente: " + name);
    return value;
  }

  private String setting(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  @Test
  void aiReturnsStructuredFinancialIntent() {
    var provider =
        new AssistantProvider(
            setting("GROQ_CHAT_COMPLETIONS_URL", "https://api.groq.com/openai/v1/chat/completions"),
            required("GROQ_API_KEY"),
            setting("GROQ_MODEL", "openai/gpt-oss-20b"),
            new ObjectMapper());
    try {
      var result = provider.interpret("Gastei exatamente R$ 45,90 no mercado hoje.");
      assertEquals(
          "transaction", result.path("intent").asText(), "Intenção estruturada incorreta.");
      assertEquals("EXPENSE", result.path("type").asText(), "Tipo estruturado incorreto.");
      assertEquals(
          0,
          new BigDecimal("45.90").compareTo(result.path("amount").decimalValue()),
          "Valor estruturado incorreto.");
      assertFalse(result.path("description").asText().isBlank(), "Descrição ausente.");
      var income = provider.interpret("Recebi exatamente R$ 123,45 de um trabalho hoje.");
      assertEquals("transaction", income.path("intent").asText(), "Intenção do ganho incorreta.");
      assertEquals("INCOME", income.path("type").asText(), "Tipo do ganho incorreto.");
      assertEquals(
          0,
          new BigDecimal("123.45").compareTo(income.path("amount").decimalValue()),
          "Valor do ganho incorreto.");
    } catch (AssistantProvider.ProviderFailure e) {
      fail(
          "GroqCloud recusou a chamada real: HTTP "
              + e.upstreamStatus()
              + ", código "
              + e.diagnosticCode()
              + ". Corpo da resposta e credenciais omitidos.");
    } catch (Exception e) {
      fail(
          "Falha na chamada real de IA ("
              + e.getClass().getSimpleName()
              + "). Confira credencial, endpoint, modelo e rede. Resposta e segredos omitidos.");
    }
  }

  @Test
  void firestoreConnectsWithCredentials() {
    try {
      String project = required("FIREBASE_PROJECT_ID");
      assertFalse(project.startsWith("demo-"), "O teste real não aceita projeto demo.");
      assertTrue(
          System.getenv("FIRESTORE_EMULATOR_HOST") == null
              || System.getenv("FIRESTORE_EMULATOR_HOST").isBlank(),
          "Desative o emulador para validação real.");
      var options =
          com.google.cloud.firestore.FirestoreOptions.newBuilder()
              .setProjectId(project)
              .setDatabaseId(setting("FIREBASE_DATABASE_ID", "(default)"))
              .setCredentials(com.google.auth.oauth2.GoogleCredentials.getApplicationDefault())
              .build();
      try (var database = options.getService()) {
        database
            .collection("finor_healthchecks")
            .document(UUID.randomUUID().toString())
            .get()
            .get(15, java.util.concurrent.TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      fail(
          "Falha no Firestore real ("
              + e.getClass().getSimpleName()
              + "). Verifique projeto, banco, IAM, credenciais e rede. Credenciais omitidas.");
    }
  }

  @Test
  void smtpAuthenticatesAndSends() {
    String recipient = required("SMTP_TEST_TO"), from = required("MAIL_FROM");
    String host = required("SPRING_MAIL_HOST"),
        username = required("SPRING_MAIL_USERNAME"),
        password = required("SPRING_MAIL_PASSWORD");
    boolean ssl =
        Boolean.parseBoolean(setting("SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE", "false"));
    boolean starttls =
        Boolean.parseBoolean(setting("SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", "true"));
    assertTrue(ssl || starttls, "O teste exige TLS ou STARTTLS.");
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
        .withPropertyValues(
            "spring.mail.host=" + host,
            "spring.mail.port=" + setting("SPRING_MAIL_PORT", "587"),
            "spring.mail.username=" + username,
            "spring.mail.password=" + password,
            "spring.mail.properties.mail.smtp.auth=true",
            "spring.mail.properties.mail.smtp.ssl.enable=" + ssl,
            "spring.mail.properties.mail.smtp.ssl.checkserveridentity=true",
            "spring.mail.properties.mail.smtp.starttls.enable=" + starttls,
            "spring.mail.properties.mail.smtp.starttls.required=" + starttls,
            "spring.mail.properties.mail.smtp.connectiontimeout=5000",
            "spring.mail.properties.mail.smtp.timeout=10000",
            "spring.mail.properties.mail.smtp.writetimeout=10000")
        .run(
            context -> {
              try {
                var sender = context.getBean(JavaMailSenderImpl.class);
                sender.testConnection();
                String marker = "FINOR-" + UUID.randomUUID();
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(recipient);
                message.setSubject("Finor — validação SMTP — " + marker);
                message.setText(
                    "Teste autorizado de integração SMTP do Finor.\n\nIdentificador: "
                        + marker
                        + "\n\n"
                        + "Nenhum dado financeiro ou código de recuperação foi incluído. Confirme o"
                        + " recebimento deste identificador para validar a entrega na caixa de"
                        + " entrada.");
                sender.send(message);
                System.out.println(
                    "SMTP aceitou a mensagem de teste. Identificador: "
                        + marker
                        + ". Entrega na caixa de entrada ainda depende de confirmação.");
              } catch (Exception e) {
                fail(
                    "Falha no SMTP real ("
                        + e.getClass().getSimpleName()
                        + "). Confira TLS, autenticação, remetente e rede. Detalhes e credenciais"
                        + " omitidos.");
              }
            });
  }
}
