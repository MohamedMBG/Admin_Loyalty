package com.example.adminloyalty.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientsSummaryRepository {

    private final FirebaseFirestore db;

    @Inject
    public ClientsSummaryRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Task<QuerySnapshot> getClients() {
        return db.collection("users")
                .orderBy("points", Query.Direction.DESCENDING)
                .limit(100)
                .get();
    }

    public Task<QuerySnapshot> getClientEarnActivities(String clientId) {
        return db.collection("users")
                .document(clientId)
                .collection("activities")
                .whereEqualTo("type", "earn")
                .get();
    }
}
