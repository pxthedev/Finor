package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finor.assistant.AssistantProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"firestore", "firestore-legacy-auth"})
@EnabledIfEnvironmentVariable(named = "FINOR_FIRESTORE_TESTS", matches = "1")
class FirestoreIntegrationTest {
  @Test
  void directClientCannotReadFirestoreDocuments() throws Exception {
    String token = access(register());
    String owner = decoder.decode(token).getSubject();
    String url =
        "http://"
            + System.getenv("FIRESTORE_EMULATOR_HOST")
            + "/v1/projects/demo-finor/databases/(default)/documents/finor_users/"
            + owner;
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
    assertEquals(403, response.statusCode());
  }

  @BeforeEach
  void noTransportRetries() {
    http.getRestTemplate()
        .setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
  }

  @Autowired TestRestTemplate http;
  @Autowired FirestoreStore store;
  @Autowired ObjectMapper json;
  @Autowired JwtDecoder decoder;
  @MockitoBean AssistantProvider provider;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("FIREBASE_PROJECT_ID", () -> "demo-finor");
    r.add(
        "JWT_SECRET",
        () -> "firestore-emulator-test-only-" + UUID.randomUUID() + UUID.randomUUID());
  }

  HttpHeaders headers(String token) {
    var h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return h;
  }

  <T> ResponseEntity<T> request(
      String path, HttpMethod method, Object body, String token, Class<T> type) {
    return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers(token)), type);
  }

  Map post(String path, Object body, String token) {
    var response = request(path, HttpMethod.POST, body, token, Map.class);
    assertTrue(response.getStatusCode().is2xxSuccessful(), response.toString());
    return response.getBody();
  }

  Map register() {
    return post(
        "/auth/register",
        Map.of(
            "name",
            "Firestore test",
            "email",
            UUID.randomUUID() + "@example.test",
            "password",
            "integration-password-123"),
        null);
  }

  String access(Map s) {
    return s.get("accessToken").toString();
  }

  Map account(String token, String amount) {
    return post(
        "/accounts",
        Map.of("name", "Principal", "type", "CHECKING", "openingBalance", amount),
        token);
  }

  Map create(String token, UUID key, Map body) {
    var h = headers(token);
    h.set("Idempotency-Key", key.toString());
    var response =
        http.exchange(
            "/api/v1/transactions", HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    assertEquals(200, response.getStatusCode().value(), response.toString());
    return response.getBody();
  }

  String date() {
    return FirestoreFinance.today().toString();
  }

  void assertMoney(String expected, Object actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
  }

  @Test
  void ledgerOwnershipIdempotencyAndDeletion() {
    String a = access(register()), other = access(register());
    String account = account(a, "100.00").get("id").toString();
    UUID key = UUID.randomUUID();
    var body = Map.of("accountId", account, "type", "EXPENSE", "amount", "20.00", "date", date());
    var first = create(a, key, body);
    assertEquals(first.get("id"), create(a, key, body).get("id"));
    var h = headers(other);
    h.set("Idempotency-Key", UUID.randomUUID().toString());
    assertEquals(
        404,
        http.exchange("/api/v1/transactions", HttpMethod.POST, new HttpEntity<>(body, h), Map.class)
            .getStatusCode()
            .value());
    var accounts = request("/accounts", HttpMethod.GET, null, a, List.class).getBody();
    assertMoney("80", ((Map) accounts.getFirst()).get("balance"));
    assertEquals(
        200,
        request("/transactions/" + first.get("id"), HttpMethod.DELETE, null, a, String.class)
            .getStatusCode()
            .value());
    create(a, key, body);
    assertTrue(request("/transactions", HttpMethod.GET, null, a, List.class).getBody().isEmpty());
  }

  @Test
  void refreshReplayRevokesFamilyAndResetRevokesOldVersion() {
    var original = register();
    String old = original.get("refreshToken").toString();
    var rotated = post("/auth/refresh", Map.of("refreshToken", old), null);
    assertEquals(
        401,
        request("/auth/refresh", HttpMethod.POST, Map.of("refreshToken", old), null, Map.class)
            .getStatusCode()
            .value());
    assertEquals(
        401,
        request("/users/me", HttpMethod.GET, null, access(rotated), Map.class)
            .getStatusCode()
            .value());
    var s = register();
    String token = access(s), owner = decoder.decode(token).getSubject();
    var profile = await(store.user(owner).get());
    String reset = UUID.randomUUID().toString();
    await(
        store
            .database()
            .collection("finor_password_resets")
            .document(hash(reset))
            .set(
                row(
                    "user_id",
                    owner,
                    "used",
                    false,
                    "auth_version",
                    0L,
                    "expires_at",
                    Instant.now().plusSeconds(300).toString())));
    post(
        "/auth/reset-password",
        Map.of("token", reset, "password", "new-integration-password-123"),
        null);
    assertEquals(
        401, request("/users/me", HttpMethod.GET, null, token, Map.class).getStatusCode().value());
    assertEquals(
        401,
        request(
                "/auth/refresh",
                HttpMethod.POST,
                Map.of("refreshToken", s.get("refreshToken")),
                null,
                Map.class)
            .getStatusCode()
            .value());
    var login =
        post(
            "/auth/login",
            Map.of("email", profile.getString("email"), "password", "new-integration-password-123"),
            null);
    assertEquals(
        200,
        request("/users/me", HttpMethod.GET, null, access(login), Map.class)
            .getStatusCode()
            .value());
  }

  @Test
  void planningBudgetAndAccountDeletion() {
    String token = access(register());
    post(
        "/onboarding",
        Map.of(
            "account",
            Map.of("name", "Principal", "type", "CHECKING", "openingBalance", "1000.00"),
            "schedule",
            Map.of(
                "monthlyAmount",
                "3200.00",
                "nextIncome",
                FirestoreFinance.today().plusDays(10).toString(),
                "remainingEssentials",
                "100.00",
                "safetyMargin",
                "50.00"),
            "bills",
            List.of(
                Map.of("name", "Internet", "amount", "100.00", "dueDate", date(), "paid", false)),
            "goal",
            Map.of("name", "Reserva", "target", "500.00", "reserved", "200.00")),
        token);
    assertMoney(
        "550",
        request("/planning/available", HttpMethod.GET, null, token, Map.class)
            .getBody()
            .get("available"));
    assertMoney(
        "270", post("/planning/simulate", Map.of("amount", "280.00"), token).get("remaining"));
    var categories = request("/categories", HttpMethod.GET, null, token, List.class).getBody();
    String category = ((Map) categories.getFirst()).get("id").toString(),
        month = YearMonth.from(FirestoreFinance.today()).toString();
    post(
        "/budgets",
        Map.of(
            "month",
            month,
            "allocations",
            List.of(Map.of("categoryId", category, "amount", "300.00"))),
        token);
    var accounts = request("/accounts", HttpMethod.GET, null, token, List.class).getBody();
    create(
        token,
        UUID.randomUUID(),
        Map.of(
            "accountId",
            ((Map) accounts.getFirst()).get("id"),
            "categoryId",
            category,
            "amount",
            "45.90",
            "type",
            "EXPENSE",
            "date",
            date()));
    var budgets =
        request("/budgets?month=" + month, HttpMethod.GET, null, token, List.class).getBody();
    assertMoney("45.90", ((Map) budgets.getFirst()).get("spent"));
    var exported = request("/users/me/export", HttpMethod.GET, null, token, Map.class).getBody();
    assertFalse(((Map) exported.get("profile")).containsKey("password_hash"));
    assertEquals(
        200,
        request(
                "/users/me",
                HttpMethod.DELETE,
                Map.of("password", "integration-password-123", "confirm", true),
                token,
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        401, request("/accounts", HttpMethod.GET, null, token, List.class).getStatusCode().value());
  }

  @Test
  void installmentPlansSaveProgressProtectOwnershipAndKeepPaidHistory() {
    String owner = access(register()), other = access(register());
    String accountId = account(owner, "1000.00").get("id").toString();
    var plan =
        post(
            "/installments",
            Map.of(
                "name",
                "Notebook",
                "installmentAmount",
                "100.00",
                "totalInstallments",
                3,
                "paidInstallments",
                1,
                "firstDueDate",
                FirestoreFinance.today().minusMonths(1).toString()),
            owner);
    String planId = plan.get("id").toString();
    assertEquals(
        404,
        request(
                "/installments/" + planId + "/payments/2",
                HttpMethod.POST,
                Map.of("accountId", accountId),
                other,
                Map.class)
            .getStatusCode()
            .value());
    post("/installments/" + planId + "/payments/2", Map.of("accountId", accountId), owner);
    post("/installments/" + planId + "/payments/2", Map.of("accountId", accountId), owner);
    var list = request("/installments", HttpMethod.GET, null, owner, List.class).getBody();
    assertEquals(2, ((Number) ((Map) list.getFirst()).get("paid_installments")).intValue());
    assertMoney(
        "900.00",
        ((Map) request("/accounts", HttpMethod.GET, null, owner, List.class).getBody().getFirst())
            .get("balance"));
    assertEquals(
        1, request("/transactions", HttpMethod.GET, null, owner, List.class).getBody().size());
    assertEquals(
        404,
        request("/installments/" + planId, HttpMethod.DELETE, null, other, Map.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        request("/installments/" + planId, HttpMethod.DELETE, null, owner, String.class)
            .getStatusCode()
            .value());
    assertTrue(
        request("/installments", HttpMethod.GET, null, owner, List.class).getBody().isEmpty());
    assertEquals(
        1, request("/transactions", HttpMethod.GET, null, owner, List.class).getBody().size());
    var goal =
        post("/goals", Map.of("name", "Reserva", "target", "1000.00", "reserved", "100.00"), owner);
    assertEquals(
        200,
        request("/goals/" + goal.get("id"), HttpMethod.DELETE, null, owner, String.class)
            .getStatusCode()
            .value());
  }

  @Test
  void anotherSalaryReceiptInTheSameMonthAddsToBalance() {
    String token = access(register());
    var salaryBody =
        Map.of(
            "monthlyAmount",
            "1000.00",
            "nextIncome",
            FirestoreFinance.today().plusDays(20).toString());
    var initial = post("/planning/salary", salaryBody, token);
    String accountId = initial.get("account_id").toString();
    create(
        token,
        UUID.randomUUID(),
        Map.of("accountId", accountId, "type", "EXPENSE", "amount", "1000.00", "date", date()));
    var receipt =
        Map.of(
            "monthlyAmount",
            "1000.00",
            "nextIncome",
            FirestoreFinance.today().plusDays(20).toString(),
            "accountId",
            accountId,
            "receiptId",
            UUID.randomUUID().toString());
    var first = post("/planning/salary", receipt, token);
    assertEquals(first.get("id"), post("/planning/salary", receipt, token).get("id"));
    assertMoney(
        "1000.00",
        ((Map) request("/accounts", HttpMethod.GET, null, token, List.class).getBody().getFirst())
            .get("balance"));
    assertEquals(
        3, request("/transactions", HttpMethod.GET, null, token, List.class).getBody().size());
  }

  @Test
  void salaryIsSavedOnceAndOtherGainsIncreaseTheSameBalance() {
    String token = access(register()), other = access(register());
    var body =
        Map.of(
            "monthlyAmount",
            "3000.00",
            "nextIncome",
            FirestoreFinance.today().plusDays(20).toString());
    var first = post("/planning/salary", body, token);
    assertEquals(first.get("id"), post("/planning/salary", body, token).get("id"));
    var changed =
        post(
            "/planning/salary",
            Map.of(
                "monthlyAmount",
                "3200.00",
                "nextIncome",
                FirestoreFinance.today().plusDays(20).toString()),
            token);
    assertEquals(first.get("id"), changed.get("id"));
    String accountId = first.get("account_id").toString();
    create(
        token,
        UUID.randomUUID(),
        Map.of(
            "accountId",
            accountId,
            "type",
            "INCOME",
            "amount",
            "350.00",
            "date",
            date(),
            "description",
            "Freelance"));
    var accounts = request("/accounts", HttpMethod.GET, null, token, List.class).getBody();
    assertEquals(1, accounts.size());
    assertMoney("3550.00", ((Map) accounts.getFirst()).get("balance"));
    assertMoney(
        "3550.00",
        request("/planning/available", HttpMethod.GET, null, token, Map.class)
            .getBody()
            .get("available"));
    assertEquals(
        2, request("/transactions", HttpMethod.GET, null, token, List.class).getBody().size());
    assertEquals(
        1, request("/planning/salary", HttpMethod.GET, null, token, List.class).getBody().size());
    assertEquals(
        404,
        request(
                "/planning/salary",
                HttpMethod.POST,
                Map.of(
                    "monthlyAmount",
                    "100.00",
                    "nextIncome",
                    FirestoreFinance.today().plusDays(20).toString(),
                    "accountId",
                    accountId),
                other,
                Map.class)
            .getStatusCode()
            .value());
  }

  @Test
  void categoriesAreCustomizablePerOwnerAndKeepTransactionsOnDeletion() {
    String token = access(register()), other = access(register());
    var categories = request("/categories", HttpMethod.GET, null, token, List.class).getBody();
    assertEquals(10, categories.size());
    Map category =
        (Map)
            categories.stream()
                .filter(c -> "Alimentação".equals(((Map) c).get("name")))
                .findFirst()
                .orElseThrow();
    String categoryId = category.get("id").toString();
    assertEquals(
        200,
        request(
                "/categories/" + categoryId,
                HttpMethod.PATCH,
                Map.of("name", "Minha feira"),
                token,
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        404,
        request(
                "/categories/" + categoryId,
                HttpMethod.PATCH,
                Map.of("name", "Alheia"),
                other,
                Map.class)
            .getStatusCode()
            .value());
    assertEquals(
        404,
        request("/categories/" + categoryId, HttpMethod.DELETE, null, other, Map.class)
            .getStatusCode()
            .value());
    String account = account(token, "100.00").get("id").toString();
    create(
        token,
        UUID.randomUUID(),
        Map.of(
            "accountId",
            account,
            "categoryId",
            categoryId,
            "type",
            "EXPENSE",
            "amount",
            "25.00",
            "date",
            date()));
    post("/categories", Map.of("name", "Meu hobby"), token);
    assertEquals(
        200,
        request("/categories/" + categoryId, HttpMethod.DELETE, null, token, String.class)
            .getStatusCode()
            .value());
    var after = request("/categories", HttpMethod.GET, null, token, List.class).getBody();
    assertEquals(10, after.size());
    assertTrue(after.stream().noneMatch(c -> "Alimentação".equals(((Map) c).get("name"))));
    var transactions = request("/transactions", HttpMethod.GET, null, token, List.class).getBody();
    assertEquals(1, transactions.size());
    assertNull(((Map) transactions.getFirst()).get("category_id"));
    assertMoney(
        "75.00",
        ((Map) request("/accounts", HttpMethod.GET, null, token, List.class).getBody().getFirst())
            .get("balance"));
  }

  @Test
  void profilePhotoPersistsIsPrivateAndCanBeRemoved() throws Exception {
    String token = access(register()), other = access(register());
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(80, 100, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
    String source = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    var saved = request("/users/me/photo", HttpMethod.PUT, Map.of("dataUrl", source), token, Map.class);
    assertEquals(200, saved.getStatusCode().value());
    String normalized = saved.getBody().get("dataUrl").toString();
    assertEquals(normalized, request("/users/me/photo", HttpMethod.GET, null, token, Map.class).getBody().get("dataUrl"));
    assertNull(request("/users/me/photo", HttpMethod.GET, null, other, Map.class).getBody().get("dataUrl"));
    assertEquals(400, request("/users/me/photo", HttpMethod.PUT, Map.of("dataUrl", "data:image/jpeg;base64,not-an-image"), token, Map.class).getStatusCode().value());
    assertEquals(normalized, request("/users/me/photo", HttpMethod.GET, null, token, Map.class).getBody().get("dataUrl"));
    assertEquals(200, request("/users/me/photo", HttpMethod.DELETE, null, token, String.class).getStatusCode().value());
    assertNull(request("/users/me/photo", HttpMethod.GET, null, token, Map.class).getBody().get("dataUrl"));
  }

  @Test
  void overviewEndpointsMatchLedgerAndCategoryReadsDoNotWriteAgain() {
    String token = access(register()), other = access(register());
    String owner = decoder.decode(token).getSubject();
    String account = account(token, "100.00").get("id").toString();
    var categories = request("/categories", HttpMethod.GET, null, token, List.class).getBody();
    Object revision = await(store.user(owner).get()).get("revision");
    request("/categories", HttpMethod.GET, null, token, List.class);
    assertEquals(revision, await(store.user(owner).get()).get("revision"));
    String category = ((Map)categories.getFirst()).get("id").toString();
    create(token, UUID.randomUUID(), Map.of("accountId", account, "categoryId", category, "type", "EXPENSE", "amount", "12.50", "date", LocalDate.now().toString(), "description", "Teste"));
    String month = YearMonth.now().toString();
    var home = request("/dashboard/home?month=" + month, HttpMethod.GET, null, token, Map.class);
    assertEquals(200, home.getStatusCode().value());
    assertMoney("87.50", ((Map)((List)home.getBody().get("accounts")).getFirst()).get("balance"));
    var report = request("/reports/overview?month=" + month, HttpMethod.GET, null, token, Map.class);
    assertEquals(200, report.getStatusCode().value());
    assertMoney("12.50", ((Map)report.getBody().get("summary")).get("expenses"));
    assertMoney("12.50", ((Map)((List)report.getBody().get("allTimeExpenses")).getFirst()).get("spent"));
    var otherReport = request("/reports/overview?month=" + month, HttpMethod.GET, null, other, Map.class);
    assertTrue(((List)otherReport.getBody().get("allTimeExpenses")).isEmpty());
  }

  @Test
  void chatHistorySurvivesReloadAndBelongsOnlyToItsOwner() throws Exception {
    String owner = access(register()), other = access(register());
    when(provider.interpret(anyString(), anyList())).thenReturn(json.readTree("{\"intent\":\"clarify\"}"));
    var first =
        post("/assistant/messages", Map.of("message", "Quero organizar minhas finanças"), owner);
    String session = first.get("sessionId").toString();
    post("/assistant/messages", Map.of("sessionId", session, "message", "Por onde começo?"), owner);
    var timeline = request("/assistant/sessions/" + session + "/timeline", HttpMethod.GET, null, owner, Map.class);
    assertEquals(200, timeline.getStatusCode().value());
    assertEquals(4, ((List)timeline.getBody().get("messages")).size());
    assertEquals(404, request("/assistant/sessions/" + session + "/timeline", HttpMethod.GET, null, other, Map.class).getStatusCode().value());

    var sessions =
        request("/assistant/sessions", HttpMethod.GET, null, owner, List.class).getBody();
    assertEquals(1, sessions.size());
    assertEquals(session, ((Map) sessions.getFirst()).get("id"));
    for (int reload = 0; reload < 2; reload++) {
      var history =
          request(
                  "/assistant/sessions/" + session + "/messages",
                  HttpMethod.GET,
                  null,
                  owner,
                  List.class)
              .getBody();
      assertEquals(4, history.size());
      assertEquals("Quero organizar minhas finanças", ((Map) history.get(0)).get("content"));
      assertEquals("user", ((Map) history.get(0)).get("role"));
      assertEquals(first.get("reply"), ((Map) history.get(1)).get("content"));
      assertEquals("assistant", ((Map) history.get(1)).get("role"));
      assertEquals("Por onde começo?", ((Map) history.get(2)).get("content"));
    }
    assertTrue(
        request("/assistant/sessions", HttpMethod.GET, null, other, List.class)
            .getBody()
            .isEmpty());
    assertEquals(
        404,
        request(
                "/assistant/sessions/" + session + "/messages",
                HttpMethod.GET,
                null,
                other,
                Map.class)
            .getStatusCode()
            .value());

    // Long conversations must not silently stop at the old 200-message limit.
    String ownerId = decoder.decode(owner).getSubject();
    Instant start = Instant.now();
    store.view(
        ownerId,
        List.of("messages"),
        true,
        s -> {
          for (int index = 0; index < 205; index++) {
            String messageId = id();
            s.put(
                "messages",
                messageId,
                row(
                    "id",
                    messageId,
                    "session_id",
                    session,
                    "role",
                    "user",
                    "content",
                    "Mensagem " + index,
                    "created_at",
                    start.plusMillis(index).toString()));
          }
          return null;
        });
    var complete =
        request(
                "/assistant/sessions/" + session + "/messages",
                HttpMethod.GET,
                null,
                owner,
                List.class)
            .getBody();
    assertEquals(209, complete.size());
    assertEquals("Mensagem 204", ((Map) complete.getLast()).get("content"));
  }

  @Test
  void assistantRequiresConfirmationAndIsIdempotent() throws Exception {
    String token = access(register()), other = access(register());
    account(token, "100.00");
    when(provider.interpret(anyString(), anyList()))
        .thenReturn(
            json.readTree(
                "{\"intent\":\"transaction\",\"amount\":45.9,\"date\":null,\"type\":\"EXPENSE\",\"description\":\"Mercado\",\"category\":null}"));
    var reply = post("/assistant/messages", Map.of("message", "Gastei 45,90 no mercado"), token);
    String id = ((Map) reply.get("action")).get("id").toString();
    assertTrue(
        request("/transactions", HttpMethod.GET, null, token, List.class).getBody().isEmpty());
    assertEquals(
        404,
        request(
                "/assistant/actions/" + id + "/confirm",
                HttpMethod.POST,
                Map.of(),
                other,
                Map.class)
            .getStatusCode()
            .value());
    var first = post("/assistant/actions/" + id + "/confirm", Map.of(), token);
    var again = post("/assistant/actions/" + id + "/confirm", Map.of(), token);
    assertEquals(first.get("id"), again.get("id"));
    assertEquals(
        1, request("/transactions", HttpMethod.GET, null, token, List.class).getBody().size());
    assertEquals(
        409,
        request("/assistant/actions/" + id + "/reject", HttpMethod.POST, Map.of(), token, Map.class)
            .getStatusCode()
            .value());
  }

  @Test
  void concurrentSameKeyCreatesOnlyOneTransaction() throws Exception {
    String token = access(register());
    String account = account(token, "100.00").get("id").toString();
    UUID key = UUID.randomUUID();
    var body = Map.of("accountId", account, "type", "EXPENSE", "amount", "10.00", "date", date());
    try (var executor = Executors.newFixedThreadPool(3)) {
      var calls = new ArrayList<Future<Map>>();
      for (int n = 0; n < 3; n++) calls.add(executor.submit(() -> create(token, key, body)));
      var ids = new HashSet<Object>();
      for (var call : calls) ids.add(call.get(45, TimeUnit.SECONDS).get("id"));
      assertEquals(1, ids.size());
    }
    assertEquals(
        1, request("/transactions", HttpMethod.GET, null, token, List.class).getBody().size());
  }
}
