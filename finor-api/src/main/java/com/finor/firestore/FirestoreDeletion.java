package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Profile("firestore")
public class FirestoreDeletion {
  private final FirestoreStore store;
  private final org.springframework.beans.factory.ObjectProvider<FirebaseIdentity> identity;

  public FirestoreDeletion(
      FirestoreStore store,
      org.springframework.beans.factory.ObjectProvider<FirebaseIdentity> identity) {
    this.store = store;
    this.identity = identity;
  }

  public void erase(String owner) {
    var root = store.user(owner);
    var profile = await(root.get());
    if (!profile.exists() || !"DELETING".equals(profile.getString("status"))) return;
    String uid = profile.getString("firebase_uid");
    if (uid != null && identity.getIfAvailable() != null) identity.getObject().deleteAuthUser(uid);
    for (var collection : root.listCollections()) {
      while (true) {
        var documents = await(collection.limit(400).get()).getDocuments();
        if (documents.isEmpty()) break;
        var batch = store.database().batch();
        documents.forEach(d -> batch.delete(d.getReference()));
        await(batch.commit());
      }
    }
    for (String name : java.util.List.of("finor_refresh_tokens", "finor_password_resets")) {
      while (true) {
        var documents =
            await(store.database().collection(name).whereEqualTo("user_id", owner).limit(400).get())
                .getDocuments();
        if (documents.isEmpty()) break;
        var batch = store.database().batch();
        documents.forEach(d -> batch.delete(d.getReference()));
        await(batch.commit());
      }
    }
    var batch = store.database().batch();
    batch.delete(
        store
            .database()
            .collection("finor_email_claims")
            .document(hash(profile.getString("email"))));
    if (uid != null)
      batch.delete(store.database().collection("finor_identity_links").document(hash(uid)));
    batch.delete(root);
    await(batch.commit());
  }

  @Scheduled(fixedDelay = 60000)
  public void retry() {
    for (var profile :
        await(
                store
                    .database()
                    .collection("finor_users")
                    .whereEqualTo("status", "DELETING")
                    .limit(20)
                    .get())
            .getDocuments()) {
      try {
        erase(profile.getId());
      } catch (RuntimeException ignored) {
        /* Retried on the next maintenance cycle; never log personal data. */
      }
    }
  }
}
