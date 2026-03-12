package com.example.adminloyalty.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CashierRepository {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    @Inject
    public CashierRepository(FirebaseFirestore db, FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
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

    public Task<QuerySnapshot> checkReceiptExists(String orderNo) {
        return db.collection("earn_codes")
                .whereEqualTo("orderNo", orderNo)
                .limit(1)
                .get();
    }

    public Task<String> createVoucherTransaction(String orderNo, double amountMAD, int validForSec, String cashierId, String cashierName) {
        return db.runTransaction(transaction -> {
            double ratio = 5.0;
            DocumentReference settingsRef = db.collection("settings").document("loyalty_config");
            DocumentSnapshot settingsSnap = transaction.get(settingsRef);
            if (settingsSnap.exists() && settingsSnap.getDouble("pointsRatio") != null) {
                ratio = settingsSnap.getDouble("pointsRatio");
            }

            int calculatedPoints = (int) Math.max(1, Math.round(amountMAD / ratio));

            DocumentReference newRef = db.collection("earn_codes").document();
            Map<String, Object> doc = new HashMap<>();
            doc.put("orderNo", orderNo);
            doc.put("amountMAD", amountMAD);
            doc.put("points", calculatedPoints);
            doc.put("status", "pending");
            doc.put("createdAt", FieldValue.serverTimestamp());
            doc.put("validForSec", validForSec);
            doc.put("nonce", randomNonce(10));
            doc.put("cashierId", cashierId);
            doc.put("cashierName", cashierName != null ? cashierName : "Unknown Cashier");

            transaction.set(newRef, doc);
            return newRef.getId();
        });
    }

    public Task<Void> cancelVoucherStatus(String voucherId) {
        DocumentReference ref = db.collection("earn_codes").document(voucherId);
        return ref.get().continueWithTask(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                String status = task.getResult().getString("status");
                if ("pending".equals(status)) {
                    return ref.update("status", "canceled");
                }
            }
            throw new Exception("Voucher cannot be canceled");
        });
    }

    public DocumentReference getVoucherReference(String voucherId) {
        return db.collection("earn_codes").document(voucherId);
    }

    private String randomNonce(int len) {
        final String alphabet = "0123456789abcdef";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
