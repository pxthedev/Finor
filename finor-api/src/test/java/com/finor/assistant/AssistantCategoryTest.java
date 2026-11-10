package com.finor.assistant;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssistantCategoryTest {
  @Test void matchesAccentsCaseAndWhitespaceWithoutCreatingDuplicates() {
    UUID id = UUID.randomUUID();
    var result = AssistantCategory.resolve("  alimentacao ", List.of(Map.of("id", id, "name", "Alimentação")),
        name -> { fail("Não deve duplicar uma categoria existente"); return null; });
    assertEquals(id, result.id());
    assertEquals("Alimentação", result.name());
  }

  @Test void createsAndLinksSpecificCustomCategory() {
    UUID id = UUID.randomUUID();
    var result = AssistantCategory.resolve("Cuidados  com pets", List.of(), name -> {
      assertEquals("Cuidados com pets", name);
      return id;
    });
    assertEquals(id, result.id());
    assertEquals("Cuidados com pets", result.name());
  }

  @Test void missingCategoryUsesExplicitFallback() {
    UUID id = UUID.randomUUID();
    var result = AssistantCategory.resolve(null, List.of(Map.of("id", id.toString(), "name", "Outros")),
        name -> { fail("Categoria padrão já existe"); return null; });
    assertEquals(id, result.id());
  }

  @Test void rejectsInvalidNamesBeforeCreating() {
    assertThrows(IllegalArgumentException.class, () -> AssistantCategory.resolve("x".repeat(81), List.of(), name -> UUID.randomUUID()));
  }
}
