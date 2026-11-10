package com.finor.assistant;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssistantProviderTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AssistantProvider provider =
      new AssistantProvider(
          AssistantProvider.ENDPOINT, "test-key-not-real", "openai/gpt-oss-20b", json);

  private JsonNode response(String arguments) {
    return json.valueToTree(
        Map.of(
            "choices",
            List.of(
                Map.of(
                    "finish_reason",
                    "tool_calls",
                    "message",
                    Map.of(
                        "role",
                        "assistant",
                        "tool_calls",
                        List.of(
                            Map.of(
                                "type",
                                "function",
                                "function",
                                Map.of("name", "interpret_finance", "arguments", arguments))))))));
  }

  private String valid() {
    return "{\"intent\":\"transaction\",\"amount\":45.9,\"date\":null,\"category\":\"Alimentação\",\"description\":\"Mercado\",\"type\":\"EXPENSE\"}";
  }

  @Test
  void requestUsesGroqChatCompletionsAndForcesOneValidatedTool() {
    var payload = json.valueToTree(provider.payload("Gastei 45 reais."));
    assertEquals("https://api.groq.com/openai/v1/chat/completions", AssistantProvider.ENDPOINT);
    assertEquals("system", payload.path("messages").get(0).path("role").asText());
    assertEquals("user", payload.path("messages").get(1).path("role").asText());
    assertFalse(payload.has("input"));
    assertFalse(payload.has("instructions"));
    assertFalse(payload.has("max_output_tokens"));
    assertTrue(payload.has("max_completion_tokens"));
    assertFalse(payload.path("parallel_tool_calls").asBoolean());
    assertEquals(
        "interpret_finance", payload.path("tool_choice").path("function").path("name").asText());
    var tool = payload.path("tools").get(0);
    assertEquals("function", tool.path("type").asText());
    assertEquals(6, tool.path("function").path("parameters").path("required").size());
    assertFalse(tool.path("function").path("parameters").path("additionalProperties").asBoolean());
  }

  @Test
  void parsesGroqToolArguments() {
    var result = provider.parse(response(valid()));
    assertTrue(result.get("date").isNull());
    assertEquals("transaction", result.path("intent").asText());
  }

  @Test
  void rejectsIncompleteResponsesAndPlainText() throws Exception {
    assertThrows(
        Exception.class,
        () ->
            provider.parse(
                json.readTree("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{}}]}")));
    assertThrows(
        Exception.class,
        () ->
            provider.parse(
                json.readTree(
                    "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Qualquer"
                        + " texto\"}}]}")));
  }

  @Test
  void rejectsMultipleOrUnexpectedCalls() {
    var value = (com.fasterxml.jackson.databind.node.ObjectNode) response(valid());
    var calls =
        (com.fasterxml.jackson.databind.node.ArrayNode)
            value.path("choices").get(0).path("message").path("tool_calls");
    calls.add(calls.get(0).deepCopy());
    assertThrows(Exception.class, () -> provider.parse(value));
    var unexpected = response(valid());
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            unexpected
                .path("choices")
                .get(0)
                .path("message")
                .path("tool_calls")
                .get(0)
                .path("function"))
        .put("name", "execute_sql");
    assertThrows(Exception.class, () -> provider.parse(unexpected));
  }

  @Test
  void rejectsUnvalidatedArguments() {
    assertThrows(Exception.class, () -> provider.parse(response("{\"intent\":\"execute_sql\"}")));
    assertThrows(
        Exception.class, () -> provider.parse(response(valid().replace("45.9", "\"45.9\""))));
  }

  @Test
  void distinguishesQuotaFromTemporaryRateLimits() {
    var quota =
        provider.failure(
            429, "{\"error\":{\"code\":\"new_billing_code\",\"type\":\"insufficient_quota\"}}");
    assertEquals("insufficient_quota", quota.diagnosticCode());
    assertTrue(quota.getReason().contains("GroqCloud"));
    var temporary = provider.failure(429, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
    assertTrue(temporary.getReason().contains("Aguarde"));
  }

  @Test
  void neverExposesUpstreamMessagesOrUnknownCodes() {
    var result =
        provider.failure(
            401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"private-key-value\"}}");
    assertFalse(result.getReason().contains("private-key-value"));
    assertEquals(
        "unclassified",
        provider.failure(400, "{\"error\":{\"code\":\"private-key-value\"}}").diagnosticCode());
  }

  @Test
  void willNotSendKeyToAnotherHost() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AssistantProvider(
                "https://example.test/v1/chat/completions", "secret", "model", json));
  }
}
