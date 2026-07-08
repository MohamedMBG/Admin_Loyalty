package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RedeemingRepository {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final AdminApiClient api;

    @Inject
    public RedeemingRepository(FirebaseFirestore db, FirebaseAuth auth, AdminApiClient api) {
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

    /**
     * Complete a customer's pending redemption via the backend. The customer creates the
     * pending code in their own app; the cashier scans it and calls this to mark it done.
     * Backend deducts nothing here — points were already reserved at redeem time.
     * Blocking — call on a background thread.
     */
    public ApiResult completeRedeem(String code) {
        JSONObject body = new JSONObject();
        try {
            body.put("code", code);
        } catch (Exception e) {
            return ApiResult.clientError("Failed to build request");
        }
        return api.post("/cashier/redeem/complete", body, AdminApiClient.newIdempotencyKey());
    }
}
