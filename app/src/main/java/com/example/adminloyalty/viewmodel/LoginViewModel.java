package com.example.adminloyalty.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.AuthenticationRepository;
import com.example.adminloyalty.data.AuthenticationRepository.Failure;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Coordinates staff sign-in and password recovery for the login screen.
 *
 * <p>The ViewModel exposes a single immutable state stream. Firebase access remains in
 * {@link AuthenticationRepository}; the Activity is responsible only for input validation,
 * localized messages, and navigation.</p>
 */
@HiltViewModel
public class LoginViewModel extends ViewModel {

    /** Operations that can put the login screen into a loading state. */
    public enum Operation {
        NONE,
        SIGN_IN,
        PASSWORD_RESET
    }

    /** Immutable login-screen state. */
    public static final class UiState {
        public final Operation operation;
        public final String authenticatedRole;
        public final Failure failure;
        public final boolean passwordResetSent;

        private UiState(@NonNull Operation operation,
                        String authenticatedRole,
                        Failure failure,
                        boolean passwordResetSent) {
            this.operation = operation;
            this.authenticatedRole = authenticatedRole;
            this.failure = failure;
            this.passwordResetSent = passwordResetSent;
        }

        /** Returns an idle state with no pending result. */
        @NonNull
        public static UiState idle() {
            return new UiState(Operation.NONE, null, null, false);
        }

        /** Returns a loading state for the supplied operation. */
        @NonNull
        public static UiState loading(@NonNull Operation operation) {
            return new UiState(operation, null, null, false);
        }

        /** Returns a successful authenticated state for an approved staff role. */
        @NonNull
        public static UiState authenticated(@NonNull String role) {
            return new UiState(Operation.NONE, role, null, false);
        }

        /** Returns a completed password-reset request state. */
        @NonNull
        public static UiState passwordResetSent() {
            return new UiState(Operation.NONE, null, null, true);
        }

        /** Returns a categorized failure state. */
        @NonNull
        public static UiState failure(@NonNull Failure failure) {
            return new UiState(Operation.NONE, null, failure, false);
        }
    }

    private final AuthenticationRepository repository;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.idle());

    /**
     * Creates the ViewModel.
     *
     * @param repository authentication data source
     */
    @Inject
    public LoginViewModel(@NonNull AuthenticationRepository repository) {
        this.repository = repository;
    }

    /** Returns the current login-screen state. */
    @NonNull
    public LiveData<UiState> getUiState() {
        return uiState;
    }

    /**
     * Starts staff authentication.
     *
     * @param email validated, normalized email address
     * @param password non-empty password
     */
    public void signIn(@NonNull String email, @NonNull String password) {
        uiState.setValue(UiState.loading(Operation.SIGN_IN));
        repository.signIn(email, password, new AuthenticationRepository.SignInCallback() {
            @Override
            public void onSuccess(@NonNull String role) {
                uiState.postValue(UiState.authenticated(role));
            }

            @Override
            public void onFailure(@NonNull Failure failure) {
                uiState.postValue(UiState.failure(failure));
            }
        });
    }

    /**
     * Sends a password-recovery request for a validated email address.
     *
     * @param email validated, normalized email address
     */
    public void sendPasswordReset(@NonNull String email) {
        uiState.setValue(UiState.loading(Operation.PASSWORD_RESET));
        repository.sendPasswordReset(email, new AuthenticationRepository.PasswordResetCallback() {
            @Override
            public void onSuccess() {
                uiState.postValue(UiState.passwordResetSent());
            }

            @Override
            public void onFailure(@NonNull Failure failure) {
                uiState.postValue(UiState.failure(failure));
            }
        });
    }

    /** Clears a handled one-time success or failure result. */
    public void clearResult() {
        uiState.setValue(UiState.idle());
    }
}
