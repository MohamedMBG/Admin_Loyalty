package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientDetailsRepository {

    private final AdminApiClient api;

    @Inject
    public ClientDetailsRepository(AdminApiClient api) {
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

    /**
     * A user's full profile for the client-details header via the backend. Blocking — call on a
     * background thread. Replaces the direct {@code users/{uid}} read (rules-denied for admins).
     * Returns {@code {uid,fullName,email,phone,gender,address,birthday,points,visits,createdAt,lastEarnAt}}.
     */
    public ApiResult getUser(String clientId) {
        return api.get("/admin/users/" + clientId);
    }
}
