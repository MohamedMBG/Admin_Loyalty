package com.example.adminloyalty.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.adminloyalty.models.ScanLog;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for scan logs with simple paging and user name caching to reduce reads.
 */
public class LogsRepository {

    private static final int USER_BATCH_LIMIT = 10;
    private final FirebaseFirestore db;
    private final Map<String, String> userNameCache = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LogsRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public LogsRepository(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public Task<QuerySnapshot> loadPage(@Nullable DocumentSnapshot lastDoc, int limit) {
        Query query = db.collection("earn_codes")
                .orderBy("redeemedAt", Query.Direction.DESCENDING)
                .whereEqualTo("status", "redeemed")
                .limit(limit);

        if (lastDoc != null) {
            query = query.startAfter(lastDoc);
        }
        return query.get();
    }

    public void mapDocuments(@NonNull QuerySnapshot snapshot, @NonNull RepoCallback callback) {
        executor.execute(() -> {
            List<ScanLog> logs = new ArrayList<>();
            List<String> missingUsers = new ArrayList<>();

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ScanLog log = mapDoc(doc);
                logs.add(log);

                String uid = trim(log.getRedeemedByUid());
                if (!uid.isEmpty()) {
                    String cached = userNameCache.get(uid);
                    if (cached != null) {
                        log.setClientName(cached);
                    } else {
                        missingUsers.add(uid);
                    }
                }
            }

            if (missingUsers.isEmpty()) {
                callback.onResult(logs, null);
            } else {
                fetchUserNames(missingUsers, logs, callback);
            }
        });
    }

    private void fetchUserNames(@NonNull List<String> userIds, @NonNull List<ScanLog> logs, @NonNull RepoCallback callback) {
        Set<String> distinct = new HashSet<>(userIds);
        List<String> ids = new ArrayList<>(distinct);

        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += USER_BATCH_LIMIT) {
            int end = Math.min(i + USER_BATCH_LIMIT, ids.size());
            List<String> batch = ids.subList(i, end);
            Task<QuerySnapshot> task = db.collection("users")
                    .whereIn("__name__", batch)
                    .get();
            tasks.add(task);
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            for (Object obj : results) {
                QuerySnapshot qs = (QuerySnapshot) obj;
                for (DocumentSnapshot d : qs.getDocuments()) {
                    String name = trim(d.getString("userDisplayName"));
                    if (name.isEmpty()) {
                        name = trim(d.getString("fullName"));
                    }
                    if (name.isEmpty()) {
                        name = trim(d.getString("name"));
                    }
                    if (name.isEmpty()) {
                        name = "Client: " + d.getId().substring(0, Math.min(6, d.getId().length()));
                    }
                    userNameCache.put(d.getId(), name);
                }
            }

            for (ScanLog log : logs) {
                String uid = trim(log.getRedeemedByUid());
                if (!uid.isEmpty()) {
                    String name = userNameCache.get(uid);
                    if (name != null) {
                        log.setClientName(name);
                    }
                }
            }
            callback.onResult(logs, null);
        }).addOnFailureListener(e -> callback.onResult(logs, e));
    }

    private ScanLog mapDoc(@NonNull DocumentSnapshot doc) {
        String id = doc.getId();
        String orderNo = doc.getString("orderNo");
        String redeemedByUid = doc.getString("redeemedByUid");
        double amount = doc.getDouble("amountMAD") != null ? doc.getDouble("amountMAD") : 0.0;
        long points = doc.getLong("points") != null ? doc.getLong("points") : 0L;
        Timestamp redeemedAt = doc.getTimestamp("redeemedAt");
        Timestamp createdAt = doc.getTimestamp("createdAt");
        String status = doc.getString("status");
        String cashierName = normalizeCashierName(doc.getString("cashierName"), doc.getString("cashier"));
        String userDisplay = trim(doc.getString("userDisplayName"));
        if (!userDisplay.isEmpty()) {
            userNameCache.put(redeemedByUid, userDisplay);
        }

        ScanLog log = new ScanLog(id, orderNo, redeemedByUid, userDisplay.isEmpty() ? "Loading..." : userDisplay,
                cashierName, amount, points, redeemedAt, createdAt, status);
        return log;
    }

    private String normalizeCashierName(@Nullable String cashierName, @Nullable String fallbackCashier) {
        String name = trim(cashierName);
        if (name.isEmpty()) {
            name = trim(fallbackCashier);
        }
        if (name.isEmpty()) {
            name = "Unknown Staff";
        }
        return name;
    }

    private String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    public interface RepoCallback {
        void onResult(@NonNull List<ScanLog> logs, @Nullable Exception error);
    }
}
