package com.finor.shared.config;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs only on the disposable local PostgreSQL fixture, never remote Supabase. */
@EnabledIfEnvironmentVariable(
    named = "FINOR_TEST_DATABASE_URL",
    matches = "jdbc:postgresql://127[.]0[.]0[.]1:.+")
class SupabaseSchemaIntegrationTest {
  @Test
  void appliesMigrationsInPrivateSchemaWithoutPublicPrivileges() throws Exception {
    String url = System.getenv("FINOR_TEST_DATABASE_URL");
    var flyway =
        Flyway.configure()
            .dataSource(url, "finor", "")
            .schemas("finor")
            .defaultSchema("finor")
            .locations("classpath:db/migration", "classpath:db/supabase")
            .load();
    flyway.migrate();
    flyway.migrate();
    try (var c = DriverManager.getConnection(url, "finor", "");
        var statement = c.createStatement()) {
      try (var result =
          statement.executeQuery(
              "SELECT count(*) FROM information_schema.tables WHERE table_schema='finor' AND"
                  + " table_name='financial_transaction'")) {
        assertTrue(result.next());
        assertEquals(1, result.getInt(1));
      }
      try (var result =
          statement.executeQuery(
              "SELECT count(*) FROM pg_namespace n CROSS JOIN LATERAL aclexplode(n.nspacl) a WHERE"
                  + " n.nspname='finor' AND a.grantee=0")) {
        assertTrue(result.next());
        assertEquals(0, result.getInt(1));
      }
    }
  }
}
