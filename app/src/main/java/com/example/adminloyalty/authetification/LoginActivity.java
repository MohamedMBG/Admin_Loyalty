package com.example.adminloyalty.authetification;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.adminloyalty.MainActivity;
import com.example.adminloyalty.cashier.CashierActivity;
import com.example.adminloyalty.databinding.ActivityLoginBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String email = binding.emailInput.getText() == null
                ? "" : binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText() == null
                ? "" : binding.passwordInput.getText().toString();

        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);

        if (email.isEmpty()) {
            binding.emailLayout.setError("Enter your email address");
            binding.emailInput.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError("Enter a valid email address");
            binding.emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.passwordLayout.setError("Enter your password");
            binding.passwordInput.requestFocus();
            return;
        }

        loginWithFirebase(email, password);
    }

    private void loginWithFirebase(String email, String password) {
        binding.loginButton.setEnabled(false);
        binding.loginButton.setText("Signing in…");

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        showSnack("Sign-in failed. Please try again.");
                        resetButton();
                        return;
                    }

                    user.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                Object roleClaim = tokenResult.getClaims().get("role");
                                String role = roleClaim instanceof String ? (String) roleClaim : "";

                                Class<?> destination;
                                if ("admin".equalsIgnoreCase(role)) {
                                    destination = MainActivity.class;
                                } else if ("cashier".equalsIgnoreCase(role)) {
                                    destination = CashierActivity.class;
                                } else {
                                    showSnack("Account role is not configured. Contact admin.");
                                    auth.signOut();
                                    resetButton();
                                    return;
                                }

                                startActivity(new Intent(LoginActivity.this, destination));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showSnack("Could not verify account role: " + e.getMessage());
                                resetButton();
                            });
                })
                .addOnFailureListener(e -> {
                    binding.passwordLayout.setError("Email or password is incorrect");
                    showSnack("Could not sign in. Check your details and try again.");
                    resetButton();
                });
    }

    private void resetButton() {
        binding.loginButton.setEnabled(true);
        binding.loginButton.setText("Sign in");
    }

    private void showSnack(String message) {
        Snackbar.make(binding.loginRoot, message, Snackbar.LENGTH_SHORT).show();
    }
}
