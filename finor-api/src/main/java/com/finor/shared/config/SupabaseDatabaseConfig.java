package com.finor.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@Profile("supabase")
public class SupabaseDatabaseConfig {
  static void validateUrl(String url) {
    if (!url.startsWith("jdbc:postgresql://"))
      throw new IllegalArgumentException("Informe a URL JDBC do Supabase.");
    URI uri = URI.create(url.substring(5));
    if (uri.getHost() == null
        || uri.getPort() != 5432
        || !"/postgres".equals(uri.getPath())
        || uri.getUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException(
          "Use Direct connection ou Session pooler na porta 5432, banco postgres, sem credenciais"
              + " nem parâmetros na URL.");
  }

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource dataSource(
      @Value("${SUPABASE_JDBC_URL}") String url,
      @Value("${SUPABASE_DB_USER}") String user,
      @Value("${SUPABASE_DB_PASSWORD}") String password,
      @Value("${SUPABASE_SSL_ROOT_CERT}") String certificate) {
    validateUrl(url);
    if (user.isBlank() || password.isBlank() || !Files.isRegularFile(Path.of(certificate)))
      throw new IllegalArgumentException(
          "Configure usuário, senha e certificado CA do Supabase no backend.");
    var source = new HikariDataSource();
    source.setJdbcUrl(url);
    source.setUsername(user);
    source.setPassword(password);
    return source;
  }
}
