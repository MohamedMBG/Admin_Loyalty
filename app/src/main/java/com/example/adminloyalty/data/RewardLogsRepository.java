package com.example.adminloyalty.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RewardLogsRepository {
    private final FirebaseFirestore db;

    @Inject
    public RewardLogsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Task<QuerySnapshot> getRedemptionLogs() {
        return db.collection("redeem_codes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get();
    }
}
