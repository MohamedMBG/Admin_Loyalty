package com.example.adminloyalty.data;

import com.example.adminloyalty.models.RewardItem;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RewardsAdminRepository {

    private final FirebaseFirestore db;
    private final String COLLECTION_NAME = "rewards_catalog";

    @Inject
    public RewardsAdminRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Query getRewardsQuery() {
        return db.collection(COLLECTION_NAME).orderBy("name");
    }

    public Task<com.google.firebase.firestore.DocumentReference> addReward(RewardItem item) {
        return db.collection(COLLECTION_NAME).add(item);
    }

    public Task<Void> updateReward(String id, RewardItem item) {
        return db.collection(COLLECTION_NAME).document(id).set(item);
    }

    public Task<Void> deleteReward(String id) {
        return db.collection(COLLECTION_NAME).document(id).delete();
    }
}
