package com.example.adminloyalty.authetification;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.adminloyalty.MainActivity;
import com.example.adminloyalty.R;
import com.example.adminloyalty.cashier.CashierActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    EditText email, password;
    Button connect;
    View root;

    // 🔹 Firebase for cashier login
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Firebase init
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI
        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);
        connect = findViewById(R.id.loginButton);
        root = findViewById(R.id.login_root);

        connect.setOnClickListener(v -> {
            String mail = email.getText().toString().trim();
            String pwd = password.getText().toString().trim();

            if (mail.isEmpty() || pwd.isEmpty()) {
                Snackbar.make(root, "Please fill all the fields!", Snackbar.LENGTH_SHORT).show();
                return; // 🔹 SUPER IMPORTANT: stop here
            }

            // -------- ADMIN (hardcoded) --------
            if (mail.equals("admin@gmail.com") && pwd.equals("TheAdmin90@@")) {
                // You wrote "invalid" here, I assume it's a joke, I left it out.
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            // -------- CASHIER (Firebase) --------
            loginCashierWithFirebase(mail, pwd);
        });
    }

    // 🔹 Login cashier with FirebaseAuth + check role in Firestore
    private void loginCashierWithFirebase(String mail, String pwd) {
        connect.setEnabled(false);
        connect.setText("Logging in...");

        mAuth.signInWithEmailAndPassword(mail, pwd)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showSnack("Login failed, please try again.");
                        resetButton();
                        return;
                    }

                    String uid = user.getUid();

                    // Optional but recommended: check that this user is a CASHIER
                    db.collection("users")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (!snap.exists()) {
                                    showSnack("Account not configured. Contact admin.");
                                    mAuth.signOut();
                                    resetButton();
                                    return;
                                }

                                String role = snap.getString("role");
                                if (role == null || !role.equalsIgnoreCase("cashier")) {
                                    showSnack("This account is not a cashier account.");
                                    mAuth.signOut();
                                    resetButton();
                                    return;
                                }

                                // ✅ All good: open CashierActivity
                                Intent intent = new Intent(LoginActivity.this, CashierActivity.class);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showSnack("Error loading profile: " + e.getMessage());
                                resetButton();
                            });
                })
                .addOnFailureListener(e -> {
                    showSnack("Login failed: " + e.getMessage());
                    resetButton();
                });
    }

    private void resetButton() {
        connect.setEnabled(true);
        connect.setText("Login");
    }

    private void showSnack(String msg) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }
}
