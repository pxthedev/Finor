package com.finor.shared;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimit extends OncePerRequestFilter {
  private record Window(long minute, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long minute = System.currentTimeMillis() / 60000;
    if (windows.size() > 10000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
    String key =
        request.getRemoteAddr()
            + ":"
            + (request.getRequestURI().startsWith("/api/v1/auth") ? "auth" : "api");
    int limit = request.getRequestURI().startsWith("/api/v1/auth") ? 20 : 180;
    if (windows.size() >= 10000 && !windows.containsKey(key)) {
      response.setStatus(429);
      return;
    }
    var current =
        windows.compute(
            key,
            (k, v) ->
                v == null || v.minute() != minute
                    ? new Window(minute, 1)
                    : new Window(minute, v.count() + 1));
    if (current.count() > limit) {
      response.setStatus(429);
      response.setHeader("Retry-After", "60");
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write("{\"status\":429,\"detail\":\"Muitas solicitações. Aguarde um minuto.\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
