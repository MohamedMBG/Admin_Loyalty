package com.example.adminloyalty.data;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Owns staff authentication and password-recovery access to Firebase Auth.
 *
 * <p>Successful sign-in is not considered complete until a freshly issued ID token contains an
 * {@code admin} or {@code cashier} role. Accounts without an approved role are signed out. Failure
 * callbacks expose only stable categories so credentials and raw Firebase details never reach the
 * UI or logs.</p>
 */
@Singleton
public class AuthenticationRepository {

    /** Stable failure categories that the UI can safely translate into user-facing messages. */
    public enum Failure {
        INVALID_CREDENTIALS,
        ACCOUNT_DISABLED,
        TOO_MANY_REQUESTS,
        NETWORK,
        ROLE_NOT_CONFIGURED,
        ROLE_VERIFICATION,
        UNKNOWN
    }

    /** Receives the authorized staff role or a categorized authentication failure. */
    public interface SignInCallback {
        void onSuccess(@NonNull String role);

        void onFailure(@NonNull Failure failure);
    }

    /** Receives password-reset completion without revealing whether an account exists. */
    public interface PasswordResetCallback {
        void onSuccess();

        void onFailure(@NonNull Failure failure);
    }

    private final FirebaseAuth auth;

    /**
     * Creates the repository around the app-wide Firebase Auth instance.
     *
     * @param auth configured Firebase Auth client
     */
    @Inject
    public AuthenticationRepository(@NonNull FirebaseAuth auth) {
        this.auth = auth;
    }

    /**
     * Authenticates credentials and verifies the server-issued staff role claim.
     *
     * @param email normalized email address
     * @param password password exactly as entered by the user
     * @param callback receives an approved role or a safe failure category
     */
    public void signIn(@NonNull String email,
                       @NonNull String password,
                       @NonNull SignInCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        auth.signOut();
                        callback.onFailure(Failure.UNKNOWN);
                        return;
                    }

                    user.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                Object roleClaim = tokenResult.getClaims().get("role");
                                String role = roleClaim instanceof String
                                        ? ((String) roleClaim).trim()
                                        : "";
                                if ("admin".equalsIgnoreCase(role)
                                        || "cashier".equalsIgnoreCase(role)) {
                                    callback.onSuccess(role.toLowerCase(Locale.ROOT));
                                    return;
                                }

                                auth.signOut();
                                callback.onFailure(Failure.ROLE_NOT_CONFIGURED);
                            })
                            .addOnFailureListener(error -> {
                                auth.signOut();
                                callback.onFailure(Failure.ROLE_VERIFICATION);
                            });
                })
                .addOnFailureListener(error -> callback.onFailure(classify(error)));
    }

    /**
     * Requests Firebase's password-reset email for the supplied address.
     *
     * <p>Firebase's response is intentionally presented generically to avoid exposing whether an
     * email is registered.</p>
     *
     * @param email normalized email address
     * @param callback receives completion or a safe failure category
     */
    public void sendPasswordReset(@NonNull String email,
                                  @NonNull PasswordResetCallback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> callback.onFailure(classify(error)));
    }

    @NonNull
    static Failure classify(@NonNull Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return Failure.NETWORK;
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return Failure.TOO_MANY_REQUESTS;
        }
        if (error instanceof FirebaseAuthInvalidUserException
                && "ERROR_USER_DISABLED".equals(
                        ((FirebaseAuthInvalidUserException) error).getErrorCode())) {
            return Failure.ACCOUNT_DISABLED;
        }
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_INVALID_CREDENTIAL".equals(code)
                    || "ERROR_INVALID_EMAIL".equals(code)
                    || "ERROR_USER_NOT_FOUND".equals(code)
                    || "ERROR_WRONG_PASSWORD".equals(code)) {
                return Failure.INVALID_CREDENTIALS;
            }
        }
        return Failure.UNKNOWN;
    }
}
