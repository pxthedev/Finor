package com.finor.user;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!firestore")
@RequestMapping("/api/v1/users/me/photo")
public class ProfilePhotoController {
  private final JdbcTemplate db;
  public ProfilePhotoController(JdbcTemplate db) { this.db = db; }

  @GetMapping
  public ProfilePhoto.Value photo(@AuthenticationPrincipal Jwt jwt) {
    var rows = db.queryForList("SELECT data_url FROM user_photo WHERE user_id=?", UUID.fromString(jwt.getSubject()));
    return new ProfilePhoto.Value(rows.isEmpty() ? null : rows.getFirst().get("data_url").toString());
  }

  @PutMapping
  public ProfilePhoto.Value save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfilePhoto.Upload upload) {
    var dataUrl = ProfilePhoto.normalize(upload.dataUrl());
    db.update("INSERT INTO user_photo(user_id,data_url) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET data_url=EXCLUDED.data_url",
        UUID.fromString(jwt.getSubject()), dataUrl);
    return new ProfilePhoto.Value(dataUrl);
  }

  @DeleteMapping
  public void remove(@AuthenticationPrincipal Jwt jwt) {
    db.update("DELETE FROM user_photo WHERE user_id=?", UUID.fromString(jwt.getSubject()));
  }
}
