package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import com.finor.user.UserController.*;
import com.finor.user.ProfilePhoto;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("firestore")
@RequestMapping("/api/v1/users/me")
public class FirestoreUserController {
  private final FirestoreStore store;

  private final FirestoreDeletion deletion;

  public FirestoreUserController(FirestoreStore store, FirestoreDeletion deletion) {
    this.store = store;

    this.deletion = deletion;
  }

  @PatchMapping
  public void update(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody com.finor.user.UserController.Profile r) {
    store.view(
        j.getSubject(),
        List.of(),
        true,
        s -> {
          s.profile.put("name", r.name().trim());
          return null;
        });
  }

  @GetMapping("/photo")
  public ProfilePhoto.Value photo(@AuthenticationPrincipal Jwt j) {
    return store.view(j.getSubject(), List.of("profile_photos"), false, s -> {
      var photo = s.table("profile_photos").get("current");
      return new ProfilePhoto.Value(photo == null ? null : field(photo, "data_url"));
    });
  }

  @PutMapping("/photo")
  public ProfilePhoto.Value savePhoto(@AuthenticationPrincipal Jwt j, @Valid @RequestBody ProfilePhoto.Upload upload) {
    String dataUrl = ProfilePhoto.normalize(upload.dataUrl());
    return store.view(j.getSubject(), List.of("profile_photos"), true, s -> {
      s.put("profile_photos", "current", row("id", "current", "data_url", dataUrl));
      return new ProfilePhoto.Value(dataUrl);
    });
  }

  @DeleteMapping("/photo")
  public void removePhoto(@AuthenticationPrincipal Jwt j) {
    store.view(j.getSubject(), List.of("profile_photos"), true, s -> {
      s.table("profile_photos").remove("current");
      return null;
    });
  }

  @GetMapping("/export")
  public Object export(@AuthenticationPrincipal Jwt j) {
    return store.view(
        j.getSubject(),
        List.of(
            "accounts",
            "categories",
            "transactions",
            "goals",
            "installments",
            "bills",
            "rules",
            "schedule",
            "budgets",
            "chats",
            "messages",
            "actions",
            "profile_photos"),
        false,
        s -> {
          var result = new LinkedHashMap<String, Object>();
          result.put("profile", FirestoreFinance.profile(s));
          for (String table : s.tables.keySet())
            result.put(table, s.rows(table).stream().map(FirestoreStore::wire).toList());
          return result;
        });
  }

  public record Confirmation(@jakarta.validation.constraints.AssertTrue boolean confirm) {}

  @DeleteMapping
  public void delete(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Confirmation r) {
    com.finor.firestore.identity.FirebaseSessionController.requireRecent(j);
    String owner = j.getSubject();
    store.view(
        owner,
        List.of(),
        true,
        s -> {
          s.profile.put("status", "DELETING");
          return null;
        });
    deletion.erase(owner);
  }

  static String field(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }
}
