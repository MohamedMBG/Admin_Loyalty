package com.example.adminloyalty.data;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Singleton
public class InboxRepository {

    private static final String API_BASE = "https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient();
    private final FirebaseAuth auth;

    @Inject
    public InboxRepository(FirebaseAuth auth) {
        this.auth = auth;
    }

    // ---------------------------
    // Networking helpers
    // ---------------------------


    public void fetchRecipientCount(JSONObject filters, InboxCallback<Integer> callback) {

        try {
            JSONObject body = new JSONObject();
            body.put("filters", filters);

            RequestBody reqBody = RequestBody.create(body.toString(), JSON_TYPE);
            withIdToken(idToken -> {
                Request.Builder rb = new Request.Builder()
                        .url(API_BASE + "/api/push/preview")
                        .post(reqBody)
                        .addHeader("Content-Type", "application/json");
                if (idToken != null) rb.addHeader("Authorization", "Bearer " + idToken);

                http.newCall(rb.build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        callback.onError("Preview failed: " + e.getMessage());
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String resp = Objects.requireNonNull(response.body()).string();
                        if (!response.isSuccessful()) {
                            callback.onError("Preview error: " + response.code());
                            return;
                        }
                        try {
                            JSONObject obj = new JSONObject(resp);
                            int count = obj.optInt("count", 0);
                            callback.onSuccess(count);
                        } catch (Exception ex) {
                            callback.onError("Bad preview response");
                        }
                    }
                });
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public void sendPush(String title, String message, JSONObject filters, InboxCallback<String> callback) {

        try {
            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("message", message);
            body.put("filters", filters);

            // Optional extra payload (deep link, screen, etc.)
            // JSONObject data = new JSONObject();
            // data.put("deepLink", "app://promo/cheesecake");
            // body.put("data", data);

            RequestBody reqBody = RequestBody.create(body.toString(), JSON_TYPE);

            withIdToken(idToken -> {
                Request.Builder rb = new Request.Builder()
                        .url(API_BASE + "/api/push/send")
                        .post(reqBody)
                        .addHeader("Content-Type", "application/json");
                if (idToken != null) rb.addHeader("Authorization", "Bearer " + idToken);

                http.newCall(rb.build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        callback.onError("Send failed: " + e.getMessage());
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String resp = Objects.requireNonNull(response.body()).string();
                        if (!response.isSuccessful()) {
                            callback.onError("Send error: " + response.code());
                            return;
                        }
                        callback.onSuccess("Push sent successfully");
                    }
                });
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private void withIdToken(Consumer<String> useToken) {
        try {
            FirebaseUser u = auth.getCurrentUser();
            if (u == null) {
                useToken.accept(null);
                return;
            }
            u.getIdToken(true).addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    useToken.accept(null);
                    return;
                }
                useToken.accept(task.getResult().getToken());
            });
        } catch (Exception e) {
            useToken.accept(null);
        }
    }
    public interface InboxCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

}
