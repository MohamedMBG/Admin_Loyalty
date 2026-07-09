package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CashierRepository {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final AdminApiClient api;

    @Inject
    public CashierRepository(FirebaseFirestore db, FirebaseAuth auth, AdminApiClient api) {
        this.db = db;
        this.auth = auth;
        this.api = api;
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

    /**
     * Mint an earn code via the backend for a MAD purchase amount. Blocking — call on a background
     * thread. The backend derives the points (pricing is backend-owned), generates the code + expiry,
     * and stores the amount for revenue reporting; the response carries the code to show.
     */
    public ApiResult createEarnCode(double amountMad) {
        JSONObject body = new JSONObject();
        try {
            body.put("amountMad", amountMad);
        } catch (Exception e) {
            return ApiResult.clientError("Failed to build request");
        }
        return api.post("/admin/earn-codes", body, AdminApiClient.newIdempotencyKey());
    }

    /** Revoke a still-active earn code. Blocking — call on a background thread. */
    public ApiResult revokeEarnCode(String code) {
        return api.post("/admin/earn-codes/" + code + "/revoke", null, AdminApiClient.newIdempotencyKey());
    }
}
