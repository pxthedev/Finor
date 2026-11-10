package com.finor.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@org.springframework.test.context.ActiveProfiles("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "FINOR_TEST_DATABASE_URL", matches = ".+")
class AssistantIntegrationTest {
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  @MockitoBean AssistantProvider provider;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("FINOR_TEST_DATABASE_URL"));
    r.add("spring.datasource.username", () -> "finor");
    r.add("spring.datasource.password", () -> "");
    r.add(
        "JWT_SECRET",
        () -> "integration-test-secret-only-" + UUID.randomUUID() + UUID.randomUUID());
  }

  HttpHeaders headers(String token) {
    var h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.setBearerAuth(token);
    return h;
  }

  Map post(String path, Object body, String token) {
    var response =
        http.exchange(
            "/api/v1" + path,
            HttpMethod.POST,
            new HttpEntity<>(body, token == null ? new HttpHeaders() : headers(token)),
            Map.class);
    assertTrue(response.getStatusCode().is2xxSuccessful(), response.toString());
    return response.getBody();
  }

  String register() {
    var result =
        post(
            "/auth/register",
            Map.of(
                "name",
                "Assistant test",
                "email",
                UUID.randomUUID() + "@example.test",
                "password",
                "integration-password-123"),
            null);
    return result.get("accessToken").toString();
  }

  @Test
  void proposalsRequireOwnerConfirmationAndCannotDuplicateTransactions() throws Exception {
    String owner = register(), other = register();
    post(
        "/accounts",
        Map.of("name", "Principal", "type", "CHECKING", "openingBalance", "1000.00"),
        owner);
    when(provider.interpret(anyString(), anyList()))
        .thenReturn(
            json.readTree(
                "{\"intent\":\"transaction\",\"amount\":45.90,\"type\":\"EXPENSE\",\"description\":\"Mercado\"}"));
    var reply = post("/assistant/messages", Map.of("message", "Gastei 45,90 no mercado"), owner);
    var action = (Map) reply.get("action");
    String id = action.get("id").toString();
    var before =
        http.exchange(
            "/api/v1/transactions", HttpMethod.GET, new HttpEntity<>(headers(owner)), List.class);
    assertTrue(before.getBody().isEmpty());
    var forbidden =
        http.exchange(
            "/api/v1/assistant/actions/" + id + "/confirm",
            HttpMethod.POST,
            new HttpEntity<>(headers(other)),
            Map.class);
    assertEquals(404, forbidden.getStatusCode().value());
    var first = post("/assistant/actions/" + id + "/confirm", Map.of(), owner);
    var second = post("/assistant/actions/" + id + "/confirm", Map.of(), owner);
    assertEquals(first.get("id"), second.get("id"));
    var after =
        http.exchange(
            "/api/v1/transactions", HttpMethod.GET, new HttpEntity<>(headers(owner)), List.class);
    assertEquals(1, after.getBody().size());
    var rejected =
        http.exchange(
            "/api/v1/assistant/actions/" + id + "/reject",
            HttpMethod.POST,
            new HttpEntity<>(headers(owner)),
            Map.class);
    assertEquals(409, rejected.getStatusCode().value());
    var another = post("/assistant/messages", Map.of("message", "Outro gasto"), owner);
    String secondId = ((Map) another.get("action")).get("id").toString();
    post("/assistant/actions/" + secondId + "/reject", Map.of(), owner);
    var cannotConfirm =
        http.exchange(
            "/api/v1/assistant/actions/" + secondId + "/confirm",
            HttpMethod.POST,
            new HttpEntity<>(headers(owner)),
            Map.class);
    assertEquals(409, cannotConfirm.getStatusCode().value());
  }
}

