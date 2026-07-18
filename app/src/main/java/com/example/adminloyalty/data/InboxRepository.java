package com.example.adminloyalty.data;

import androidx.annotation.NonNull;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Admin push-campaign data access through the secured Bean Loyal backend.
 * <p>
 * Preview and send both use {@code /api/v1/admin/push}, replacing the previous unrelated Vercel
 * service. Authentication and 401 refresh are handled by {@link AdminApiClient}; calls run on a
 * private IO executor and never block the UI thread. Send requests carry an idempotency key so a
 * retry cannot duplicate a campaign.
 */
@Singleton
public class InboxRepository {

    private final AdminApiClient api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public InboxRepository(AdminApiClient api) {
        this.api = api;
    }

    /** Preview unique reachable customers matching the supplied audience filters. */
    public void fetchRecipientCount(@NonNull JSONObject filters,
                                    @NonNull InboxCallback<Integer> callback) {
        executor.execute(() -> {
            JSONObject body = new JSONObject();
            try {
                body.put("filters", filters);
            } catch (Exception e) {
                callback.onError("Could not build audience preview");
                return;
            }
            ApiResult result = api.post("/admin/push/preview", body, null);
            if (!result.isOk() || result.data == null) {
                callback.onError(ApiErrors.message(result, "Admin access is required.",
                        "Could not preview this audience."));
                return;
            }
            callback.onSuccess(result.data.optInt("count", 0));
        });
    }

    /** Send one idempotent push campaign to the selected audience. */
    public void sendPush(@NonNull String title, @NonNull String message,
                         @NonNull JSONObject filters, @NonNull InboxCallback<String> callback) {
        executor.execute(() -> {
            JSONObject body = new JSONObject();
            try {
                body.put("title", title);
                body.put("message", message);
                body.put("filters", filters);
            } catch (Exception e) {
                callback.onError("Could not build push campaign");
                return;
            }
            ApiResult result = api.post("/admin/push/send", body,
                    AdminApiClient.newIdempotencyKey());
            if (!result.isOk() || result.data == null) {
                callback.onError(ApiErrors.message(result, "Admin access is required.",
                        "Could not send the campaign."));
                return;
            }
            callback.onSuccess(deliveryMessage(result.data));
        });
    }

    static String deliveryMessage(JSONObject data) {
        int success = data.optInt("successCount", 0);
        int failed = data.optInt("failureCount", 0);
        int users = data.optInt("reachableUsers", 0);
        if (failed > 0) {
            return "Sent to " + success + " device(s) for " + users
                    + " customer(s); " + failed + " delivery failure(s).";
        }
        return "Push sent to " + users + " customer(s) on " + success + " device(s).";
    }

    /** Callback invoked on the repository IO thread; ViewModels should use LiveData.postValue. */
    public interface InboxCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }
}
