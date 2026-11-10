package com.finor.firestore;

import com.finor.auth.AuthController.*;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("firestore-legacy-auth")
@RequestMapping("/api/v1/auth")
public class FirestoreAuthController {
  private final FirestoreAuth auth;

  public FirestoreAuthController(FirestoreAuth auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public FirestoreAuth.Session register(@Valid @RequestBody Register r) {
    return auth.register(r.name(), r.email(), r.password());
  }

  @PostMapping("/login")
  public FirestoreAuth.Session login(@Valid @RequestBody Login r) {
    return auth.login(r.email(), r.password());
  }

  @PostMapping("/refresh")
  public FirestoreAuth.Session refresh(@Valid @RequestBody Token r) {
    return auth.refresh(r.refreshToken());
  }

  @PostMapping("/logout")
  public void logout(@Valid @RequestBody Token r) {
    auth.logout(r.refreshToken());
  }
}

