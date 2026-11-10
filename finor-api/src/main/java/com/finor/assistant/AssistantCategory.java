package com.finor.assistant;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;

/** Resolve names without silently dropping new categories. */
public final class AssistantCategory {
  private AssistantCategory() {}
  public record Resolved(UUID id, String name) {}

  public static String normalize(String name) {
    return Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  public static Resolved resolve(String proposed, List<Map<String, Object>> categories,
      Function<String, UUID> create) {
    String name = proposed == null || proposed.isBlank() ? "Outros" : proposed.trim().replaceAll("\\s+", " ");
    if (name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Categoria inválida.");
    for (var category : categories) {
      if (normalize(name).equals(normalize(category.get("name").toString())))
        return new Resolved(UUID.fromString(category.get("id").toString()), category.get("name").toString());
    }
    return new Resolved(create.apply(name), name);
  }
}
