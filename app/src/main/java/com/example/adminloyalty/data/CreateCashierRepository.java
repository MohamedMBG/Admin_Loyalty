package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateCashierRepository {

    private final AdminApiClient api;
    private final Map<String, String> pendingKeys = new ConcurrentHashMap<>();

    @Inject
    public CreateCashierRepository(AdminApiClient api) {
        this.api = api;
    }

    /**
     * Provision a cashier via the backend. Blocking — call on a background thread. Replaces the old
     * client-side flow (a secondary FirebaseApp to create the auth user + a direct users-doc write):
     * the backend now creates the auth account, sets the {@code role: cashier} custom claim (which the
     * client SDK cannot do), and writes the profile doc. A transport/5xx retry reuses the key for
     * the same request fingerprint, while successful or definitive 4xx responses release it.
     */
    public ApiResult createCashier(String name, String email, String password) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("name", name);
        } catch (Exception e) {
            return ApiResult.clientError("Failed to build request");
        }
        String fingerprint = fingerprint(body.toString());
        String key = pendingKeys.computeIfAbsent(fingerprint,
                ignored -> AdminApiClient.newIdempotencyKey());
        ApiResult result = api.post("/admin/cashiers", body, key);
        if (result.isOk() || (result.httpStatus >= 400 && result.httpStatus < 500
                && result.httpStatus != 429)) {
            pendingKeys.remove(fingerprint, key);
        }
        return result;
    }

    /** Hashes the password-bearing payload so retries can be correlated without retaining secrets. */
    static String fingerprint(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
