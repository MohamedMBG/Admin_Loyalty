package com.example.adminloyalty.data;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class CreateCashierRepository {

    private final FirebaseFirestore db;
    private final Context context;

    @Inject
    public CreateCashierRepository(FirebaseFirestore db, @ApplicationContext Context context) {
        this.db = db;
        this.context = context;
    }

    public Task<AuthResult> createCashierAuth(String email, String password) {
        String appName = "SecondaryCashierApp";
        FirebaseApp secondaryApp;
        try {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            secondaryApp = FirebaseApp.initializeApp(context, options, appName);
        } catch (IllegalStateException e) {
            secondaryApp = FirebaseApp.getInstance(appName);
        }

        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        return secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> secondaryAuth.signOut());
    }

    public Task<Void> saveCashierToFirestore(String uid, String name, String email) {
        Map<String, Object> cashierData = new HashMap<>();
        cashierData.put("uid", uid);
        cashierData.put("name", name);
        cashierData.put("email", email);
        cashierData.put("role", "cashier");
        cashierData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        cashierData.put("isActive", true);

        return db.collection("users").document(uid).set(cashierData);
    }
}
