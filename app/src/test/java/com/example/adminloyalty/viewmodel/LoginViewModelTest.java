package com.example.adminloyalty.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.adminloyalty.data.AuthenticationRepository;
import com.example.adminloyalty.data.AuthenticationRepository.Failure;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LoginViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private FakeAuthenticationRepository repository;
    private LoginViewModel viewModel;

    @Before
    public void setUp() {
        repository = new FakeAuthenticationRepository();
        viewModel = new LoginViewModel(repository);
    }

    @Test
    public void signIn_emitsLoadingThenApprovedRole() {
        viewModel.signIn("admin@example.com", "secret");

        LoginViewModel.UiState loading = viewModel.getUiState().getValue();
        assertEquals(LoginViewModel.Operation.SIGN_IN, loading.operation);

        assertEquals("admin@example.com", repository.email);
        assertEquals("secret", repository.password);
        repository.signInCallback.onSuccess("admin");

        LoginViewModel.UiState result = viewModel.getUiState().getValue();
        assertEquals(LoginViewModel.Operation.NONE, result.operation);
        assertEquals("admin", result.authenticatedRole);
        assertNull(result.failure);
    }

    @Test
    public void signIn_emitsCategorizedFailure() {
        viewModel.signIn("admin@example.com", "wrong");

        assertEquals("admin@example.com", repository.email);
        assertEquals("wrong", repository.password);
        repository.signInCallback.onFailure(Failure.INVALID_CREDENTIALS);

        LoginViewModel.UiState result = viewModel.getUiState().getValue();
        assertEquals(Failure.INVALID_CREDENTIALS, result.failure);
        assertNull(result.authenticatedRole);
    }

    @Test
    public void passwordReset_emitsCompletionWithoutAccountDisclosure() {
        viewModel.sendPasswordReset("admin@example.com");

        LoginViewModel.UiState loading = viewModel.getUiState().getValue();
        assertEquals(LoginViewModel.Operation.PASSWORD_RESET, loading.operation);

        assertEquals("admin@example.com", repository.email);
        repository.passwordResetCallback.onSuccess();

        LoginViewModel.UiState result = viewModel.getUiState().getValue();
        assertTrue(result.passwordResetSent);
        assertNull(result.failure);
        assertFalse(result.operation == LoginViewModel.Operation.PASSWORD_RESET);
    }

    private static final class FakeAuthenticationRepository extends AuthenticationRepository {
        private String email;
        private String password;
        private SignInCallback signInCallback;
        private PasswordResetCallback passwordResetCallback;

        private FakeAuthenticationRepository() {
            super(null);
        }

        @Override
        public void signIn(@NonNull String email,
                           @NonNull String password,
                           @NonNull SignInCallback callback) {
            this.email = email;
            this.password = password;
            this.signInCallback = callback;
        }

        @Override
        public void sendPasswordReset(@NonNull String email,
                                      @NonNull PasswordResetCallback callback) {
            this.email = email;
            this.passwordResetCallback = callback;
        }
    }
}
