package com.example.adminloyalty.data.api;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Synchronous REST client for the BeanLoyal backend (/api/v1).
 * Auth bearer + 401 refresh handled by {@link AuthInterceptor}. Call on a background thread.
 *
 * Idempotency: generate one key per logical write with {@link #newIdempotencyKey()} and reuse it
 * across network retries of the SAME action so a replay doesn't re-charge. Header on writes only.
 */
@Singleton
public class AdminApiClient {

    // ponytail: single hardcoded base URL. Move to BuildConfig flavors (dev/staging/prod) when
    // real staging/prod URLs exist — see plan §6. TODO(owner): set the real backend base URL.
    private static final String BASE_URL = "https://TODO-set-backend-base-url/api/v1";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;

    @Inject
    public AdminApiClient(AuthInterceptor authInterceptor) {
        this.http = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    /** GET path (relative to /api/v1), e.g. "/admin/audit?limit=50". */
    public ApiResult get(String path) {
        Request req = new Request.Builder().url(BASE_URL + path).get().build();
        return execute(req);
    }

    /** POST a JSON body. Pass idempotencyKey for economy writes; null to omit the header. */
    public ApiResult post(String path, @Nullable JSONObject body, @Nullable String idempotencyKey) {
        RequestBody rb = RequestBody.create(body == null ? "{}" : body.toString(), JSON);
        Request.Builder builder = new Request.Builder().url(BASE_URL + path).post(rb);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        return execute(builder.build());
    }

    private ApiResult execute(Request req) {
        try (Response response = http.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            return ApiResult.from(response, body);
        } catch (Exception e) {
            return ApiResult.networkError(e.getMessage() != null ? e.getMessage() : "Network error");
        }
    }
}
