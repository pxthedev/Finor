package com.finor.transaction;

import java.util.List;

public final class CategoryDefaults {
  private CategoryDefaults() {}

  public static final List<String> NAMES =
      List.of(
          "Alimentação",
          "Transporte",
          "Moradia",
          "Saúde",
          "Educação",
          "Lazer",
          "Compras",
          "Contas",
          "Salário",
          "Outros");
}
