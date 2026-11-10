package com.finor.shared.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SupabaseDatabaseConfigTest {
  @Test
  void acceptsDirectAndSessionConnections() {
    SupabaseDatabaseConfig.validateUrl("jdbc:postgresql://db.project.supabase.co:5432/postgres");
    SupabaseDatabaseConfig.validateUrl(
        "jdbc:postgresql://aws-0-region.pooler.supabase.com:5432/postgres");
  }

  @Test
  void rejectsTransactionPoolAndTlsOverrides() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SupabaseDatabaseConfig.validateUrl("jdbc:postgresql://host:6543/postgres"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SupabaseDatabaseConfig.validateUrl(
                "jdbc:postgresql://host:5432/postgres?sslmode=disable"));
  }

  @Test
  void rejectsCredentialsInUrl() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SupabaseDatabaseConfig.validateUrl(
                "jdbc:postgresql://user:password@host:5432/postgres"));
  }
}
