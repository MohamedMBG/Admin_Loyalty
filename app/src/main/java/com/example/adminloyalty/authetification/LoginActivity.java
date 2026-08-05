package com.example.adminloyalty.authetification;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.MainActivity;
import com.example.adminloyalty.R;
import com.example.adminloyalty.cashier.CashierActivity;
import com.example.adminloyalty.data.AuthenticationRepository.Failure;
import com.example.adminloyalty.databinding.ActivityLoginBinding;
import com.example.adminloyalty.viewmodel.LoginViewModel;
import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Validates staff credentials, observes authentication state, and routes approved roles.
 *
 * <p>Firebase access and role verification are delegated to the login ViewModel/repository. The
 * screen also provides password recovery without revealing whether an email is registered.</p>
 */
@AndroidEntryPoint
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.forgotPasswordButton.setOnClickListener(v -> attemptPasswordReset());
        binding.passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        viewModel.getUiState().observe(this, this::renderState);
    }

    private void attemptLogin() {
        String email = binding.emailInput.getText() == null
                ? "" : binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText() == null
                ? "" : binding.passwordInput.getText().toString();

        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);

        if (email.isEmpty()) {
            binding.emailLayout.setError(getString(R.string.error_enter_email));
            binding.emailInput.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError(getString(R.string.error_invalid_email));
            binding.emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.passwordLayout.setError(getString(R.string.error_enter_password));
            binding.passwordInput.requestFocus();
            return;
        }

        viewModel.signIn(email, password);
    }

    private void attemptPasswordReset() {
        String email = binding.emailInput.getText() == null
                ? "" : binding.emailInput.getText().toString().trim();

        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);

        if (email.isEmpty()) {
            binding.emailLayout.setError(getString(R.string.error_enter_email));
            binding.emailInput.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError(getString(R.string.error_invalid_email));
            binding.emailInput.requestFocus();
            return;
        }

        viewModel.sendPasswordReset(email);
    }

    private void renderState(LoginViewModel.UiState state) {
        boolean signingIn = state.operation == LoginViewModel.Operation.SIGN_IN;
        boolean sendingReset = state.operation == LoginViewModel.Operation.PASSWORD_RESET;
        boolean loading = signingIn || sendingReset;

        binding.loginButton.setEnabled(!loading);
        binding.loginButton.setText(signingIn ? R.string.signing_in : R.string.sign_in);
        binding.forgotPasswordButton.setEnabled(!loading);
        binding.forgotPasswordButton.setText(
                sendingReset ? R.string.sending_reset_link : R.string.forgot_password);
        binding.emailInput.setEnabled(!loading);
        binding.passwordInput.setEnabled(!loading);

        if (state.authenticatedRole != null) {
            Class<?> destination = "admin".equals(state.authenticatedRole)
                    ? MainActivity.class
                    : CashierActivity.class;
            Intent intent = new Intent(this, destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if (state.passwordResetSent) {
            showSnack(getString(R.string.password_reset_sent));
            viewModel.clearResult();
            return;
        }

        if (state.failure != null) {
            showFailure(state.failure);
            viewModel.clearResult();
        }
    }

    private void showFailure(Failure failure) {
        int message;
        switch (failure) {
            case INVALID_CREDENTIALS:
                binding.passwordLayout.setError(getString(R.string.error_invalid_credentials));
                message = R.string.error_invalid_credentials_recovery;
                break;
            case ACCOUNT_DISABLED:
                message = R.string.error_account_disabled;
                break;
            case TOO_MANY_REQUESTS:
                message = R.string.error_too_many_requests;
                break;
            case NETWORK:
                message = R.string.error_auth_network;
                break;
            case ROLE_NOT_CONFIGURED:
                message = R.string.error_role_not_configured;
                break;
            case ROLE_VERIFICATION:
                message = R.string.error_role_verification;
                break;
            default:
                message = R.string.error_sign_in_failed;
                break;
        }
        showSnack(getString(message));
    }

    private void showSnack(String message) {
        Snackbar.make(binding.loginRoot, message, Snackbar.LENGTH_SHORT).show();
    }
}
