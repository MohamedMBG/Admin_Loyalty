package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

    /**
     * A user's recent activity feed via the backend. Blocking — call on a background thread.
     * Replaces the old direct earn_codes/redeem_codes reads (both rules-denied). Returns the
     * canonical activity schema: {@code {activities:[{type,pointsDelta,refId,balanceAfter,createdAt}]}}.
     */
    public ApiResult getUserActivity(String clientId, int limit) {
        return api.get("/admin/users/" + clientId + "/activity?limit=" + limit);
    }

    // ponytail: BACKEND GAP — no endpoint returns another user's full profile (gender / address /
    // lastVisit) by uid; Firestore rules deny the direct read once deployed. Kept as-is so the
    // header still renders pre-cutover. Migrate to a GET /admin/users/{uid} endpoint when it exists.
    public Task<DocumentSnapshot> getUserProfile(String clientId) {
        return db.collection("users").document(clientId).get();
    }
}
