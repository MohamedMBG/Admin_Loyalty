package com.example.adminloyalty.data;

import com.example.adminloyalty.models.Promotion;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PromotionsRepository {

    private final FirebaseFirestore db;

    @Inject
    public PromotionsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public Query getPromotionsQuery() {
        return db.collection("promotions").orderBy("priority", Query.Direction.DESCENDING);
    }

    public Task<com.google.firebase.firestore.DocumentReference> addPromotion(Promotion promotion) {
        return db.collection("promotions").add(promotion);
    }

    public Task<Void> updatePromotionStatus(String id, boolean isActive) {
        return db.collection("promotions").document(id).update("active", isActive);
    }

    public Task<Void> deletePromotion(String id) {
        return db.collection("promotions").document(id).delete();
    }
}
