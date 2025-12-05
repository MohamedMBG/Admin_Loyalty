package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateCashierFragment extends Fragment {

    private TextInputEditText etName, etEmail, etPassword;
    private MaterialButton btnCreate;
    private ProgressBar progressBar;
    private ImageView btnBack;

    private FirebaseFirestore db; // Use default DB (Admin)

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create_cashier, container, false);

        db = FirebaseFirestore.getInstance();

        initViews(v);

        return v;
    }

    private void initViews(View v) {
        etName = v.findViewById(R.id.etName);
        etEmail = v.findViewById(R.id.etEmail);
        etPassword = v.findViewById(R.id.etPassword);
        btnCreate = v.findViewById(R.id.btnCreate);
        progressBar = v.findViewById(R.id.progressBar);
        btnBack = v.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(view -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        btnCreate.setOnClickListener(view -> validateAndCreate());
    }

    private void validateAndCreate() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email required");
            return;
        }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            etPassword.setError("Password must be 6+ chars");
            return;
        }

        createCashierAccount(name, email, pass);
    }

    /**
     * Creates a user WITHOUT logging out the current Admin.
     * Uses a secondary FirebaseApp instance.
     */
    private void createCashierAccount(String name, String email, String password) {
        setLoading(true);

        // 1. Initialize a secondary app instance
        String appName = "SecondaryCashierApp";
        FirebaseApp secondaryApp = null;
        try {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            secondaryApp = FirebaseApp.initializeApp(getContext(), options, appName);
        } catch (IllegalStateException e) {
            // App already exists, get it
            secondaryApp = FirebaseApp.getInstance(appName);
        }

        // 2. Get Auth for this secondary app
        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

        // 3. Create the user
        FirebaseApp finalSecondaryApp = secondaryApp;
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveCashierToFirestore(uid, name, email);

                    // Cleanup: Sign out the temp user and delete the app instance ref if possible
                    secondaryAuth.signOut();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "Creation Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveCashierToFirestore(String uid, String name, String email) {
        // We use the MAIN 'db' instance here because the Admin is logged in there
        // and has permission to write to the DB.

        Map<String, Object> cashierData = new HashMap<>();
        cashierData.put("uid", uid);
        cashierData.put("name", name);
        cashierData.put("email", email);
        cashierData.put("role", "cashier");
        cashierData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        cashierData.put("isActive", true);

        // Save to "users" collection (or "cashiers" if you prefer separate)
        db.collection("users").document(uid)
                .set(cashierData)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "Cashier Account Created!", Toast.LENGTH_SHORT).show();
                    clearForm();
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "DB Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(!isLoading);
        etName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }

    private void clearForm() {
        etName.setText("");
        etEmail.setText("");
        etPassword.setText("");
    }
}