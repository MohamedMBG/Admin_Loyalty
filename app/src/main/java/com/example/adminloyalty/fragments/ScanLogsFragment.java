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
import com.example.adminloyalty.data.LogsRepository;
import com.example.adminloyalty.models.ScanLog;
import com.google.android.gms.tasks.Task;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ScanLogsFragment extends Fragment {

    private static final int PAGE_SIZE = 50;
    private static final String TAG_DATE_PICKER = "DATE_PICKER";

    private RecyclerView recyclerViewLogs;
    private ProgressBar progressBar;
    private TextView tvLogCount;
    private TextView tvTotalEarning;
    private ImageView btnBack;
    private ChipGroup chipGroupFilters;

    private ScanLogAdapter adapter;
    private final List<ScanLog> displayedLogs = new ArrayList<>();
    private final List<ScanLog> allLogs = new ArrayList<>();

    private LogsRepository logsRepository;
    private DocumentSnapshot lastSnapshot;
    private boolean isLoading = false;
    private boolean hasMore = true;

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
        logsRepository = new LogsRepository();

        loadNextPage();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (logsRepository != null) {
            logsRepository.shutdown();
        }
    }

    private void bindViews(@NonNull View view) {
        recyclerViewLogs = view.findViewById(R.id.recyclerViewLogs);
        progressBar = view.findViewById(R.id.progressBar);
        tvLogCount = view.findViewById(R.id.tvLogCount);
        tvTotalEarning = view.findViewById(R.id.TotalEarning);
        btnBack = view.findViewById(R.id.btnBack);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);
    }

    private void setupRecycler() {
        adapter = new ScanLogAdapter();
        recyclerViewLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewLogs.setAdapter(adapter);
        recyclerViewLogs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                if (!isLoading && hasMore && lastVisible >= adapter.getItemCount() - 5) {
                    loadNextPage();
                }
            }
        });
    }

    private void setupBack() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
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
                long endInclusive = endUtcMs + TimeUnit.DAYS.toMillis(1) - 1;
                for (ScanLog log : allLogs) {
                    long time = getLogTimeMs(log);
                    if (time != -1 && time >= startUtcMs && time <= endInclusive) {
                        displayedLogs.add(log);
                    }
                }
                break;
        }

        adapter.submitList(new ArrayList<>(displayedLogs));
        refreshHeader();
    }

    private long getLogTimeMs(@NonNull ScanLog log) {
        Timestamp ts = log.getRedeemedAt() != null ? log.getRedeemedAt() : log.getCreatedAt();
        if (ts == null || ts.toDate() == null) return -1;
        return ts.toDate().getTime();
    }

    private void loadNextPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        showLoading(true);

        Task<QuerySnapshot> task = logsRepository.loadPage(lastSnapshot, PAGE_SIZE);
        task.addOnCompleteListener(this::handlePageResult);
    }

    private void handlePageResult(@NonNull Task<QuerySnapshot> task) {
        showLoading(false);
        isLoading = false;

        if (!task.isSuccessful() || task.getResult() == null) {
            toast("Failed to load records");
            return;
        }

        QuerySnapshot snapshot = task.getResult();
        if (snapshot.isEmpty()) {
            hasMore = false;
            return;
        }

        logsRepository.mapDocuments(snapshot, (logs, error) -> {
            if (error != null) {
                toast("Partial data loaded");
            }
            if (!isAdded()) return;

            allLogs.addAll(logs);
            displayedLogs.clear();
            displayedLogs.addAll(allLogs);
            adapter.submitList(new ArrayList<>(displayedLogs));
            refreshHeader();

            List<DocumentSnapshot> docs = snapshot.getDocuments();
            lastSnapshot = docs.get(docs.size() - 1);
            hasMore = docs.size() >= PAGE_SIZE;
        });
    }

    private void refreshHeader() {
        updateCountText();
        updateTotalsUI();
    }

    private void updateTotalsUI() {
        if (!isAdded()) return;

        double total = 0.0;
        for (ScanLog log : displayedLogs) {
            total += log.getAmountMAD();
        }

        tvTotalEarning.setText(String.format(Locale.getDefault(), "Total: %.2f MAD", total));
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
