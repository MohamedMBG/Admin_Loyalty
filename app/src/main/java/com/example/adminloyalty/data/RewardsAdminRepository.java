package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.models.RewardItem;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RewardsAdminRepository {

    private static final String COLLECTION_NAME = "rewards_catalog";

    private final FirebaseFirestore db;
    private final AdminApiClient api;

    @Inject
    public RewardsAdminRepository(FirebaseFirestore db, AdminApiClient api) {
        this.db = db;
        this.api = api;
    }

    /**
     * Live catalog query — stays a direct Firestore read (rules allow any signed-in user to read
     * {@code rewards_catalog}). Only the writes below are backend-owned.
     */
    public Query getRewardsQuery() {
        return db.collection(COLLECTION_NAME).orderBy("name");
    }

    /** Create a reward via the backend. Blocking — call on a background thread. */
    public ApiResult addReward(RewardItem item) {
        return api.post("/admin/rewards", rewardBody(item), AdminApiClient.newIdempotencyKey());
    }

    /** Overwrite a reward via the backend. Blocking — call on a background thread. */
    public ApiResult updateReward(String id, RewardItem item) {
        return api.put("/admin/rewards/" + id, rewardBody(item), AdminApiClient.newIdempotencyKey());
    }

    /** Delete a reward via the backend. Blocking — call on a background thread. */
    public ApiResult deleteReward(String id) {
        return api.delete("/admin/rewards/" + id, AdminApiClient.newIdempotencyKey());
    }

    // Maps the admin RewardItem (costPoints/isVisible) onto the backend catalog schema (cost/active).
    private static JSONObject rewardBody(RewardItem item) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", item.getName());
            body.put("cost", item.getCostPoints());
            body.put("category", item.getCategory());
            body.put("active", item.isVisible());
        } catch (Exception ignored) {
            // JSONObject.put only throws on NaN/Infinity keys/values — none here.
        }
        return body;
    }
}
