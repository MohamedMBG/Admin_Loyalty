package com.example.adminloyalty.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.models.ScanLog;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

/**
 * Scan logs via {@code GET /admin/earn-codes}. Admins can't read {@code earn_codes} directly
 * (Firestore rules), so the log reads through the backend, which resolves cashier + client names
 * inline — the old client-side users batch-fetch join is gone.
 */
public class LogsRepository {

    // Backend caps at 200 and exposes no pagination cursor yet, so we load a single page.
    // ponytail: a 200-row cap will eventually hide older records; add backend cursor paging then.
    private static final int PAGE_LIMIT = 200;

    private final AdminApiClient api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Inject
    public LogsRepository(@NonNull AdminApiClient api) {
        this.api = api;
    }

    public void shutdown() {
        executor.shutdown();
    }

    /** Loads recent scanned (status {@code used}) earn codes, newest scan first. Callback on main. */
    public void loadLogs(@NonNull RepoCallback callback) {
        executor.execute(() -> {
            ApiResult result = api.get("/admin/earn-codes?limit=" + PAGE_LIMIT);
            if (!result.isOk() || result.data == null) {
                String msg = result.message != null ? result.message : "Failed to load records";
                post(callback, new ArrayList<>(), new Exception(msg));
                return;
            }

            List<ScanLog> logs = new ArrayList<>();
            JSONArray items = result.data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    // Scan-log screen shows redeemed codes only; backend returns all statuses.
                    if (!"used".equals(item.optString("status"))) continue;
                    logs.add(mapItem(item));
                }
            }

            // Backend orders by createdAt DESC; the screen wants redeemedAt DESC (scan time).
            Collections.sort(logs, (a, b) -> Long.compare(scanMs(b), scanMs(a)));

            post(callback, logs, null);
        });
    }

    private ScanLog mapItem(@NonNull JSONObject item) {
        String code = item.optString("code", "");
        double amountMad = item.optDouble("amountMad", 0.0);
        long points = item.optLong("points", 0L);
        String status = item.optString("status", "");
        String clientUid = item.optString("clientUid", "");

        String clientName = cleanName(item.optString("clientName", ""));
        if (clientName.isEmpty()) {
            clientName = "Client: " + shortId(clientUid);
        }
        String cashierName = cleanName(item.optString("cashierName", ""));
        if (cashierName.isEmpty()) {
            cashierName = "Unknown Staff";
        }

        long createdMs = item.optLong("createdAt", 0L);
        long redeemedMs = item.optLong("redeemedAt", 0L);
        Timestamp createdAt = createdMs > 0 ? new Timestamp(new Date(createdMs)) : null;
        Timestamp redeemedAt = redeemedMs > 0 ? new Timestamp(new Date(redeemedMs)) : null;

        // id and orderNo both carry the code — the adapter renders "Order #<code>".
        return new ScanLog(code, code, clientUid, clientName, cashierName,
                amountMad, points, redeemedAt, createdAt, status);
    }

    private void post(@NonNull RepoCallback callback, @NonNull List<ScanLog> logs, @Nullable Exception error) {
        main.post(() -> callback.onResult(logs, error));
    }

    private static long scanMs(@NonNull ScanLog log) {
        Timestamp ts = log.getRedeemedAt() != null ? log.getRedeemedAt() : log.getCreatedAt();
        return ts != null ? ts.toDate().getTime() : 0L;
    }

    private static String cleanName(@Nullable String s) {
        String t = s == null ? "" : s.trim();
        return t.equalsIgnoreCase("null") ? "" : t;
    }

    private static String shortId(@Nullable String uid) {
        if (uid == null || uid.isEmpty()) return "?";
        return uid.substring(0, Math.min(6, uid.length()));
    }

    public interface RepoCallback {
        void onResult(@NonNull List<ScanLog> logs, @Nullable Exception error);
    }
}
