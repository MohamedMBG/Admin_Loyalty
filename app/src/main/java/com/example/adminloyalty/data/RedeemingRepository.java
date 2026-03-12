package com.example.adminloyalty.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RedeemingRepository {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    @Inject
    public RedeemingRepository(FirebaseFirestore db, FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    public String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public String getCurrentUserEmail() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;
    }

    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return db.collection("users").document(uid).get();
    }

    public Task<QuerySnapshot> searchUserByEmail(String email) {
        return db.collection("users").whereEqualTo("email", email).limit(1).get();
    }

    public Task<QuerySnapshot> searchUserByPhone(String phone) {
        return db.collection("users").whereEqualTo("phone", phone).limit(1).get();
    }

    public Task<QuerySnapshot> searchUserByUid(String uid) {
        return db.collection("users").whereEqualTo("uid", uid).limit(1).get();
    }

    public Task<QuerySnapshot> searchUserByName(String name) {
        return db.collection("users").whereEqualTo("fullName", name).limit(1).get();
    }

    public Task<QuerySnapshot> getActivePromotions() {
        return db.collection("promotions").whereEqualTo("active", true).get();
    }

    public Task<QuerySnapshot> getRewardsCatalog() {
        return db.collection("rewards_catalog").orderBy("name").get();
    }

    public Task<String> createRedeemCode(String userDocId, String selectedUserUid, String selectedUserName, String selectedItemDocId, String selectedItemName, String selectedPromoDocId, String cashierId, String cashierName) {
        return db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(userDocId);
            DocumentSnapshot userSnap = transaction.get(userRef);

            DocumentReference rewardRef = db.collection("rewards_catalog").document(selectedItemDocId);
            DocumentSnapshot rewardSnap = transaction.get(rewardRef);

            if (!userSnap.exists()) throw new IllegalStateException("User does not exist.");
            if (!rewardSnap.exists()) throw new IllegalStateException("Reward no longer exists.");

            long currentPoints = userSnap.getLong("points") != null ? userSnap.getLong("points") : 0;
            long officialCost = rewardSnap.getLong("costPoints") != null ? rewardSnap.getLong("costPoints") : 999999;

            if (selectedPromoDocId != null) {
                DocumentSnapshot promoSnap = transaction.get(db.collection("promotions").document(selectedPromoDocId));
                if (promoSnap.exists() && promoSnap.getBoolean("active")) {
                     // Extra rules could go here
                }
            }

            if (currentPoints < officialCost) {
                throw new IllegalStateException("Insufficient points (latest: " + currentPoints + ")");
            }

            transaction.update(userRef, "points", currentPoints - officialCost);
            transaction.update(userRef, "lastVisitTimestamp", FieldValue.serverTimestamp());

            DocumentReference newLogRef = db.collection("redeem_codes").document();
            Map<String, Object> data = new HashMap<>();
            data.put("userUid", selectedUserUid);
            data.put("userDocId", userDocId);
            data.put("userName", selectedUserName);
            data.put("itemName", selectedItemName);
            data.put("itemId", selectedItemDocId);
            data.put("costPoints", officialCost);
            data.put("appliedPromoId", selectedPromoDocId);
            data.put("cashierId", cashierId);
            if (cashierName != null) data.put("cashierName", cashierName);
            data.put("status", "completed");
            data.put("type", "REDEEM");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("completedAt", FieldValue.serverTimestamp());

            transaction.set(newLogRef, data);

            return "REDEEM|" + newLogRef.getId() + "|" + selectedUserUid + "|" + officialCost;
        });
    }
}
