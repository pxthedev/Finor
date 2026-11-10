package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import com.google.firebase.auth.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("firestore & !firestore-legacy-auth")
public class FirebaseIdentity {
  private final FirestoreStore store;
  private final FirebaseAuth auth;

  public FirebaseIdentity(FirestoreStore store, FirebaseAuth auth) {
    this.store = store;
    this.auth = auth;
  }

  private String linkId(String uid) {
    return hash(uid);
  }

  public String owner(FirebaseToken token) {
    String uid = token.getUid();
    var link =
        await(store.database().collection("finor_identity_links").document(linkId(uid)).get());
    if (link.exists()) return link.getString("user_id");
    try {
      UUID.fromString(uid);
      var existing = await(store.user(uid).get());
      if (existing.exists() && Objects.equals(existing.getString("email"), email(token)))
        return uid;
    } catch (IllegalArgumentException ignored) {
    }
    return UUID.nameUUIDFromBytes(("finor:firebase:" + uid).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private String email(FirebaseToken token) {
    String email = token.getEmail();
    if (email == null || email.isBlank())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Use uma conta com e-mail no Firebase Authentication.");
    return email.trim().toLowerCase(Locale.ROOT);
  }

  public Object bootstrap(FirebaseToken token, String owner) {
    String uid = token.getUid(), email = email(token);
    var linkRef = store.database().collection("finor_identity_links").document(linkId(uid));
    var claimRef = store.database().collection("finor_email_claims").document(hash(email));
    var userRef = store.user(owner);
    return await(
        store
            .database()
            .runTransaction(
                tx -> {
                  var link = tx.get(linkRef).get();
                  var claim = tx.get(claimRef).get();
                  var profile = tx.get(userRef).get();
                  if (link.exists() && !owner.equals(link.getString("user_id")))
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Identidade já vinculada.");
                  if (claim.exists() && !owner.equals(claim.getString("user_id")))
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Há uma conta anterior com esse e-mail. A migração segura precisa ser"
                            + " concluída antes do acesso.");
                  if (profile.exists()) {
                    if (!"ACTIVE".equals(profile.getString("status")))
                      throw new ResponseStatusException(
                          HttpStatus.FORBIDDEN, "Conta indisponível.");
                    String bound = profile.getString("firebase_uid");
                    if (bound != null && !bound.equals(uid))
                      throw new ResponseStatusException(
                          HttpStatus.CONFLICT, "Conta vinculada a outra identidade.");
                    // Legacy UUIDs are imported as Firebase UIDs by the explicit administrator
                    // migration.
                    if (bound == null && !uid.equals(owner))
                      throw new ResponseStatusException(
                          HttpStatus.CONFLICT, "Migração de identidade necessária.");
                    tx.update(
                        userRef,
                        row(
                            "firebase_uid",
                            uid,
                            "password_hash",
                            com.google.cloud.firestore.FieldValue.delete(),
                            "auth_version",
                            com.google.cloud.firestore.FieldValue.delete()));
                  } else {
                    tx.create(
                        userRef,
                        row(
                            "id",
                            owner,
                            "firebase_uid",
                            uid,
                            "email",
                            email,
                            "name",
                            token.getName() == null ? "Usuário" : token.getName(),
                            "onboarded",
                            false,
                            "status",
                            "ACTIVE",
                            "created_at",
                            Instant.now().toString()));
                  }
                  tx.set(linkRef, row("user_id", owner, "firebase_uid", uid));
                  tx.set(claimRef, row("user_id", owner));
                  return Map.of("userId", owner, "firebaseUid", uid);
                }));
  }

  public FirebaseToken verify(String raw) throws FirebaseAuthException {
    return auth.verifyIdToken(raw, true);
  }

  public void revoke(String uid) {
    try {
      auth.revokeRefreshTokens(uid);
    } catch (FirebaseAuthException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível encerrar as sessões.");
    }
  }

  public void deleteAuthUser(String uid) {
    try {
      auth.deleteUser(uid);
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND)
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Exclusão de identidade pendente.");
    }
  }
}
