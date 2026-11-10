package com.finor.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record Register(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  public record Login(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}

  public record Token(@NotBlank @Size(max = 200) String refreshToken) {}

  @PostMapping("/register")
  public AuthService.Session register(@Valid @RequestBody Register request) {
    return auth.register(request.name(), request.email(), request.password());
  }

  @PostMapping("/login")
  public AuthService.Session login(@Valid @RequestBody Login request) {
    return auth.login(request.email(), request.password());
  }

  @PostMapping("/refresh")
  public AuthService.Session refresh(@Valid @RequestBody Token request) {
    return auth.refresh(request.refreshToken());
  }

  @PostMapping("/logout")
  public void logout(@Valid @RequestBody Token request) {
    auth.logout(request.refreshToken());
  }
}

