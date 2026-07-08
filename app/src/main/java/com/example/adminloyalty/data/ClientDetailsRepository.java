package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientDetailsRepository {

    private final FirebaseFirestore db;
    private final AdminApiClient api;

    @Inject
    public ClientDetailsRepository(FirebaseFirestore db, AdminApiClient api) {
        this.db = db;
        this.api = api;
    }

    /**
     * Adjust a user's points via the backend. Blocking — call on a background thread.
     * delta may be negative; reason is required by the backend and audited.
     */
    public ApiResult adjustPoints(String uid, int delta, String reason) {
        JSONObject body = new JSONObject();
        try {
            body.put("delta", delta);
            body.put("reason", reason);
        } catch (Exception e) {
            return ApiResult.clientError("Failed to build request");
        }
        return api.post("/admin/users/" + uid + "/points-adjustment", body, AdminApiClient.newIdempotencyKey());
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
