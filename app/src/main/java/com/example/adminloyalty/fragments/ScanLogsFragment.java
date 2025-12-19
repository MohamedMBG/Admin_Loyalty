package com.example.adminloyalty.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.adapters.ScanLogAdapter;
import com.example.adminloyalty.models.ScanLog;
import com.google.android.gms.tasks.Task;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ScanLogsFragment extends Fragment {

    private static final int LOG_LIMIT = 200;
    private static final String TAG_DATE_PICKER = "DATE_PICKER";

    private RecyclerView recyclerViewLogs;
    private ProgressBar progressBar;
    private TextView tvLogCount;
    private ImageView btnBack;
    private ChipGroup chipGroupFilters;

    private ScanLogAdapter adapter;
    private final List<ScanLog> displayedLogs = new ArrayList<>();
    private final List<ScanLog> allLogs = new ArrayList<>();

    private FirebaseFirestore db;

    // Cache for userId -> name (prevents repeated reads)
    private final Map<String, String> userNameCache = new HashMap<>();

    private enum FilterType { ALL, CASHIER, DATE }

    public ScanLogsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_logs, container, false);

        bindViews(view);
        setupRecycler();
        setupBack();
        setupFilters(view);

        loadScanLogs();
        return view;
    }

    private void bindViews(@NonNull View view) {
        recyclerViewLogs = view.findViewById(R.id.recyclerViewLogs);
        progressBar = view.findViewById(R.id.progressBar);
        tvLogCount = view.findViewById(R.id.tvLogCount);
        btnBack = view.findViewById(R.id.btnBack);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);
        db = FirebaseFirestore.getInstance();
    }

    private void setupRecycler() {
        adapter = new ScanLogAdapter(displayedLogs);
        recyclerViewLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewLogs.setAdapter(adapter);
    }

    private void setupBack() {
        // Better than calling requireActivity().onBackPressed() directly.
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // let system handle back, or pop backstack
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                }
        );

        btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupFilters(@NonNull View view) {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) return;

            if (checkedIds.contains(R.id.filterAll)) {
                applyFilter(FilterType.ALL, null, 0L, 0L);
            }
        });

        Chip chipDate = view.findViewById(R.id.filterDate);
        chipDate.setOnClickListener(v -> {
            chipGroupFilters.check(R.id.filterDate);
            showDateRangePicker();
        });

        Chip chipCashier = view.findViewById(R.id.filterCashier);
        chipCashier.setOnClickListener(v -> {
            chipGroupFilters.check(R.id.filterCashier);
            showCashierFilterDialog();
        });
    }

    private void showDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select Dates")
                        .setSelection(androidx.core.util.Pair.create(
                                MaterialDatePicker.thisMonthInUtcMilliseconds(),
                                MaterialDatePicker.todayInUtcMilliseconds()
                        ))
                        .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            applyFilter(FilterType.DATE, null, selection.first, selection.second);
        });

        picker.addOnNegativeButtonClickListener(v -> resetToAllFilter());
        picker.addOnCancelListener(dialog -> resetToAllFilter());

        picker.show(getParentFragmentManager(), TAG_DATE_PICKER);
    }

    private void showCashierFilterDialog() {
        if (allLogs.isEmpty()) {
            toast("No data loaded yet");
            resetToAllFilter();
            return;
        }

        List<String> cashiers = getUniqueCashiers(allLogs);
        if (cashiers.isEmpty()) {
            toast("No cashier info found");
            resetToAllFilter();
            return;
        }

        String[] array = cashiers.toArray(new String[0]);

        new AlertDialog.Builder(requireContext())
                .setTitle("Filter by Cashier")
                .setItems(array, (dialog, which) -> applyFilter(FilterType.CASHIER, array[which], 0L, 0L))
                .setNegativeButton("Cancel", (dialog, which) -> resetToAllFilter())
                .show();
    }

    private void resetToAllFilter() {
        if (!isAdded()) return;
        chipGroupFilters.check(R.id.filterAll);
        applyFilter(FilterType.ALL, null, 0L, 0L);
    }

    private List<String> getUniqueCashiers(@NonNull List<ScanLog> logs) {
        Set<String> set = new HashSet<>();
        for (ScanLog log : logs) {
            String name = safeTrim(log.getCashierName());
            if (!name.isEmpty() && !"null".equalsIgnoreCase(name)) set.add(name);
        }
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    private void applyFilter(@NonNull FilterType type, @Nullable String cashier, long startUtcMs, long endUtcMs) {
        displayedLogs.clear();

        switch (type) {
            case ALL:
                displayedLogs.addAll(allLogs);
                break;

            case CASHIER:
                for (ScanLog log : allLogs) {
                    if (equalsIgnoreCaseSafe(log.getCashierName(), cashier)) {
                        displayedLogs.add(log);
                    }
                }
                break;

            case DATE:
                long endInclusive = endUtcMs + TimeUnit.DAYS.toMillis(1) - 1; // inclusive end of day
                for (ScanLog log : allLogs) {
                    long time = getLogTimeMs(log);
                    if (time != -1 && time >= startUtcMs && time <= endInclusive) {
                        displayedLogs.add(log);
                    }
                }
                break;
        }

        adapter.notifyDataSetChanged();
        updateCountText();
    }

    private long getLogTimeMs(@NonNull ScanLog log) {
        Timestamp ts = log.getRedeemedAt() != null ? log.getRedeemedAt() : log.getCreatedAt();
        if (ts == null || ts.toDate() == null) return -1;
        return ts.toDate().getTime();
    }

    private void loadScanLogs() {
        showLoading(true);

        db.collection("earn_codes")
                .orderBy("redeemedAt", Query.Direction.DESCENDING)
                .limit(LOG_LIMIT)
                .get()
                .addOnCompleteListener(this::onLogsLoaded);
    }

    private void onLogsLoaded(@NonNull Task<QuerySnapshot> task) {
        showLoading(false);

        if (!task.isSuccessful() || task.getResult() == null) {
            if (isAdded()) tvLogCount.setText("Failed to load records");
            return;
        }

        allLogs.clear();
        displayedLogs.clear();

        List<String> userIdsToFetch = new ArrayList<>();

        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
            ScanLog log = mapDocToScanLog(doc);

            allLogs.add(log);

            String uid = safeTrim(log.getRedeemedByUid());
            if (!uid.isEmpty() && !"null".equalsIgnoreCase(uid)) {
                // If cached, apply immediately
                String cached = userNameCache.get(uid);
                if (cached != null) {
                    log.setClientName(cached);
                } else {
                    // placeholder for now, batch fetch later
                    log.setClientName("Loading...");
                    userIdsToFetch.add(uid);
                }
            } else {
                log.setClientName("Unknown ID");
            }
        }

        displayedLogs.addAll(allLogs);
        adapter.notifyDataSetChanged();
        updateCountText();

        // Batch fetch user names (reduces reads)
        batchFetchClientNamesDistinct(userIdsToFetch);
    }

    private ScanLog mapDocToScanLog(@NonNull DocumentSnapshot doc) {
        String id = doc.getId();
        String orderNo = doc.getString("orderNo");
        String redeemedByUid = doc.getString("redeemedByUid");

        String cashierName = normalizeCashierName(
                doc.getString("cashierName"),
                doc.getString("cashier")
        );

        double amountMAD = doc.getDouble("amountMAD") != null ? doc.getDouble("amountMAD") : 0.0;
        long points = doc.getLong("points") != null ? doc.getLong("points") : 0L;

        Timestamp redeemedAt = doc.getTimestamp("redeemedAt");
        Timestamp createdAt = doc.getTimestamp("createdAt");
        String status = doc.getString("status");

        return new ScanLog(
                id,
                orderNo,
                redeemedByUid,
                "Loading...",
                cashierName,
                amountMAD,
                points,
                redeemedAt,
                createdAt,
                status
        );
    }

    private String normalizeCashierName(@Nullable String cashierName, @Nullable String fallbackCashier) {
        String name = safeTrim(cashierName);
        if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
            name = safeTrim(fallbackCashier);
        }
        if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
            return "Unknown Staff";
        }
        return name;
    }

    /**
     * Fetch user names in batches using whereIn (Firestore limit = 10).
     */
    private void batchFetchClientNamesDistinct(@NonNull List<String> userIds) {
        if (userIds.isEmpty()) return;

        // distinct
        Set<String> distinct = new HashSet<>();
        for (String id : userIds) {
            String uid = safeTrim(id);
            if (!uid.isEmpty() && !"null".equalsIgnoreCase(uid)) distinct.add(uid);
        }

        List<String> ids = new ArrayList<>(distinct);

        // Firestore whereIn limit is 10 values per query
        final int BATCH = 10;
        for (int i = 0; i < ids.size(); i += BATCH) {
            int end = Math.min(i + BATCH, ids.size());
            List<String> chunk = ids.subList(i, end);

            db.collection("users")
                    .whereIn("__name__", chunk)
                    .get()
                    .addOnSuccessListener(qs -> applyFetchedUsers(qs, chunk))
                    .addOnFailureListener(e -> {
                        // fallback: mark as short ID
                        for (String uid : chunk) {
                            putNameFallback(uid);
                        }
                        notifyAllVisibleItemsChanged();
                    });
        }
    }

    private void applyFetchedUsers(@NonNull QuerySnapshot qs, @NonNull List<String> requestedUids) {
        // Map fetched docs
        Map<String, String> fetched = new HashMap<>();
        for (DocumentSnapshot d : qs.getDocuments()) {
            String uid = d.getId();
            String name = safeTrim(d.getString("fullName"));
            if (name.isEmpty()) name = safeTrim(d.getString("name"));
            if (!name.isEmpty()) fetched.put(uid, name);
        }

        // Fill cache for all requested, even missing ones
        for (String uid : requestedUids) {
            String name = fetched.get(uid);
            if (name != null) {
                userNameCache.put(uid, name);
            } else {
                putNameFallback(uid);
            }
        }

        // Apply to logs
        boolean changed = false;
        for (ScanLog log : allLogs) {
            String uid = safeTrim(log.getRedeemedByUid());
            if (requestedUids.contains(uid)) {
                String newName = userNameCache.get(uid);
                if (newName != null && !newName.equals(log.getClientName())) {
                    log.setClientName(newName);
                    changed = true;
                }
            }
        }

        if (changed) notifyAllVisibleItemsChanged();
    }

    private void putNameFallback(@NonNull String uid) {
        String shortId = uid.substring(0, Math.min(uid.length(), 6));
        userNameCache.put(uid, "Client: " + shortId);
    }

    private void notifyAllVisibleItemsChanged() {
        if (!isAdded()) return;
        // simplest + safe: refresh visible list (you can optimize later with DiffUtil)
        adapter.notifyDataSetChanged();
    }

    private void updateCountText() {
        if (!isAdded()) return;

        int count = displayedLogs.size();
        if (count == 0) {
            tvLogCount.setText("No records found");
        } else {
            tvLogCount.setText(String.format(Locale.getDefault(), "%d records found", count));
        }
    }

    private void showLoading(boolean show) {
        if (!isAdded()) return;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toast(@NonNull String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private static String safeTrim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean equalsIgnoreCaseSafe(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
