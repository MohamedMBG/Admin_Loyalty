package com.example.adminloyalty.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientDetailsRepository {

    private final FirebaseFirestore db;

    @Inject
    public ClientDetailsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Task<DocumentSnapshot> getUserProfile(String clientId) {
        return db.collection("users").document(clientId).get();
    }

    public Task<QuerySnapshot> getUserEarnCodes(String clientId) {
        return db.collection("earn_codes")
                .whereEqualTo("redeemedByUid", clientId)
                .whereEqualTo("status", "redeemed")
                .get();
    }

    public Task<QuerySnapshot> getUserRedeemCodes(String clientId) {
        return db.collection("redeem_codes")
                .whereEqualTo("userUid", clientId)
                .whereEqualTo("status", "completed")
                .get();
    }

    public Task<QuerySnapshot> getCashiers() {
        return db.collection("users")
                .whereEqualTo("role", "cashier")
                .get();
    }
}
