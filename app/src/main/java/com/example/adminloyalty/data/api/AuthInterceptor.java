package com.example.adminloyalty.data.api;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Injects Firebase ID token as Bearer on every request.
 * On 401, retries once with a force-refreshed token (needed right after a role grant — §5b).
 * Runs on OkHttp's background thread, so blocking Tasks.await is safe here.
 */
public class AuthInterceptor implements Interceptor {

    private final FirebaseAuth auth;

    public AuthInterceptor(FirebaseAuth auth) {
        this.auth = auth;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        String token = getToken(false);
        Response response = chain.proceed(withBearer(original, token));

        if (response.code() == 401) {
            response.close();
            String fresh = getToken(true);
            response = chain.proceed(withBearer(original, fresh));
        }
        return response;
    }

    private Request withBearer(Request req, String token) {
        if (token == null) return req;
        return req.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    private String getToken(boolean forceRefresh) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return null;
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(forceRefresh));
            return result.getToken();
        } catch (Exception e) {
            return null;
        }
    }
}
