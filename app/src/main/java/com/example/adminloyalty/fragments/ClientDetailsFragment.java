package com.example.adminloyalty.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ClientDetailsFragment extends Fragment {

    private static final String ARG_CLIENT_ID = "client_id";
    private String clientId;
    private FirebaseFirestore db;

    // UI Views
    private TextView tvName, tvEmail, tvPhone, tvGender, tvAddress, tvPoints, tvSpend, tvEmptyHistory;
    private RecyclerView rvHistory;
    private ProgressBar progressBar;
    private ChipGroup chipGroupFilters;
    private ImageView btnBack;

    // Data
    private List<ActivityItem> allActivities = new ArrayList<>();
    private ActivityAdapter adapter;

    public ClientDetailsFragment() {
        // Required empty public constructor
    }

    public static ClientDetailsFragment newInstance(String clientId) {
        ClientDetailsFragment fragment = new ClientDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLIENT_ID, clientId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            clientId = getArguments().getString(ARG_CLIENT_ID);
        }
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_client_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        btnBack = view.findViewById(R.id.btnBack);
        tvName = view.findViewById(R.id.tvDetailName);
        tvEmail = view.findViewById(R.id.tvDetailEmail);
        tvPhone = view.findViewById(R.id.tvDetailPhone);
        tvGender = view.findViewById(R.id.tvDetailGender);
        tvAddress = view.findViewById(R.id.tvDetailAddress);
        tvPoints = view.findViewById(R.id.tvDetailPoints);
        tvSpend = view.findViewById(R.id.tvDetailSpend);
        rvHistory = view.findViewById(R.id.rvHistory);
        progressBar = view.findViewById(R.id.progressBarHistory);
        tvEmptyHistory = view.findViewById(R.id.tvEmptyHistory);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);

        // Setup Toolbar Action
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Setup Filters
        setupFilters();

        // Load Data
        loadUserProfile();
        loadHistory();
    }

    private void setupFilters() {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int id = checkedIds.get(0);
            if (id == R.id.filterAll) {
                filterList("ALL");
            } else if (id == R.id.filterScans) {
                filterList("EARN");
            } else if (id == R.id.filterGifts) {
                filterList("SPEND");
            } else if (id == R.id.filterCashier) {
                showCashierFilterDialog();
            } else if (id == R.id.filterDate) {
                showDateRangePicker();
            }
        });
    }

    private void showDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> datePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select dates")
                        .setSelection(androidx.core.util.Pair.create(
                                MaterialDatePicker.thisMonthInUtcMilliseconds(),
                                MaterialDatePicker.todayInUtcMilliseconds()
                        ))
                        .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Long startDate = selection.first;
            Long endDate = selection.second;
            if (startDate != null && endDate != null) {
                filterListByDate(startDate, endDate);
            }
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void filterListByDate(long start, long end) {
        if (allActivities == null || allActivities.isEmpty()) return;

        List<ActivityItem> filtered = new ArrayList<>();
        // Add 1 day to end date to make it inclusive (selection returns midnight)
        long endOfDay = end + (24 * 60 * 60 * 1000) - 1;

        for (ActivityItem item : allActivities) {
            if (item.getTs() != null) {
                long itemTime = item.getTs().toDate().getTime();
                if (itemTime >= start && itemTime <= endOfDay) {
                    filtered.add(item);
                }
            }
        }
        updateAdapter(filtered);
        Toast.makeText(getContext(), "Filtered by Date", Toast.LENGTH_SHORT).show();
    }

    private void showCashierFilterDialog() {
        if (allActivities == null || allActivities.isEmpty()) {
            Toast.makeText(getContext(), "No data to filter", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract unique cashiers
        Set<String> cashiers = new HashSet<>();
        for (ActivityItem item : allActivities) {
            if (item.getCashierName() != null) {
                cashiers.add(item.getCashierName());
            }
        }

        if (cashiers.isEmpty()) {
            Toast.makeText(getContext(), "No cashier data found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] cashierArray = cashiers.toArray(new String[0]);

        new AlertDialog.Builder(getContext())
                .setTitle("Filter by Cashier")
                .setItems(cashierArray, (dialog, which) -> {
                    String selectedCashier = cashierArray[which];
                    filterListByCashier(selectedCashier);
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear Filter", (dialog, which) -> filterList("ALL"))
                .show();
    }

    private void filterListByCashier(String cashierName) {
        List<ActivityItem> filtered = new ArrayList<>();
        for (ActivityItem item : allActivities) {
            if (item.getCashierName() != null && item.getCashierName().equals(cashierName)) {
                filtered.add(item);
            }
        }
        updateAdapter(filtered);
    }

    private void filterList(String type) {
        if (allActivities == null || allActivities.isEmpty()) return;

        List<ActivityItem> filtered = new ArrayList<>();

        if (type.equals("ALL")) {
            filtered.addAll(allActivities);
        } else {
            for (ActivityItem item : allActivities) {
                if (item.getType() != null && type.equalsIgnoreCase(item.getType())) {
                    filtered.add(item);
                }
            }
        }

        // Default Sort: Newest First
        Collections.sort(filtered, (o1, o2) -> {
            if (o1.getTs() == null || o2.getTs() == null) return 0;
            return o2.getTs().compareTo(o1.getTs());
        });

        updateAdapter(filtered);
    }

    private void updateAdapter(List<ActivityItem> items) {
        if (adapter == null) {
            setupRecyclerView(items);
        } else {
            adapter.updateList(items);
        }

        if (items.isEmpty()) {
            tvEmptyHistory.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvEmptyHistory.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
    }

    private void loadUserProfile() {
        if (clientId == null) return;

        db.collection("users").document(clientId).get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || !document.exists()) return;

                    String name = document.getString("fullName");
                    String email = document.getString("email");
                    String phone = document.getString("phone");
                    String gender = document.getString("gender");
                    String address = document.getString("address");

                    tvName.setText(name != null ? name : "Unknown");
                    tvEmail.setText(email != null ? email : "-");
                    tvPhone.setText(phone != null ? phone : "No Phone");
                    tvGender.setText(gender != null ? capitalize(gender) : "-");
                    tvAddress.setText(address != null ? address : "No Address");

                    Long points = document.getLong("points");
                    Long visits = document.getLong("visits");
                    long pVal = points != null ? points : 0;
                    long vVal = visits != null ? visits : 0;

                    tvPoints.setText(String.format(Locale.US, "%,d", pVal));

                    double avg = 0.0;
                    if (vVal > 0) avg = (double) pVal / vVal;
                    Double dbAvg = document.getDouble("avgSpend");
                    if (dbAvg != null) avg = dbAvg;

                    tvSpend.setText(String.format(Locale.US, "%.2f", avg));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error loading profile", Toast.LENGTH_SHORT).show()
                );
    }

    private void loadHistory() {
        if (clientId == null) return;

        progressBar.setVisibility(View.VISIBLE);

        // 1. Fetch EARN history (Scans)
        Task<QuerySnapshot> earnTask = db.collection("earn_codes")
                .whereEqualTo("redeemedByUid", clientId)
                .whereEqualTo("status", "redeemed")
                .get();

        // 2. Fetch SPEND history (Gifts)
        Task<QuerySnapshot> spendTask = db.collection("redeem_codes")
                .whereEqualTo("userUid", clientId)
                .whereEqualTo("status", "completed")
                .get();

        // Run both queries in parallel
        Tasks.whenAllSuccess(earnTask, spendTask).addOnSuccessListener(results -> {
            if (!isAdded()) return;
            progressBar.setVisibility(View.GONE);
            allActivities.clear();

            // --- Process Scans (Earn) ---
            QuerySnapshot earnSnap = (QuerySnapshot) results.get(0);
            for (DocumentSnapshot doc : earnSnap.getDocuments()) {
                try {
                    Long pts = doc.getLong("points");
                    long points = pts != null ? pts : 0;
                    String cashier = doc.getString("cashierName");
                    Timestamp ts = doc.getTimestamp("redeemedAt");

                    ActivityItem item = new ActivityItem("earn", points, null, cashier, ts);
                    allActivities.add(item);
                } catch (Exception e) {
                    Log.e("History", "Error parsing earn log", e);
                }
            }

            // --- Process Gifts (Spend) ---
            QuerySnapshot spendSnap = (QuerySnapshot) results.get(1);
            for (DocumentSnapshot doc : spendSnap.getDocuments()) {
                try {
                    Long pts = doc.getLong("costPoints");
                    long points = pts != null ? pts : 0;
                    String itemName = doc.getString("itemName");
                    String cashier = doc.getString("cashierName");
                    Timestamp ts = doc.getTimestamp("completedAt");

                    ActivityItem item = new ActivityItem("spend", points, itemName, cashier, ts);
                    allActivities.add(item);
                } catch (Exception e) {
                    Log.e("History", "Error parsing spend log", e);
                }
            }

            // Initial Filter & Sort (Date Descending)
            filterList("ALL");

        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView(List<ActivityItem> items) {
        adapter = new ActivityAdapter(items);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setAdapter(adapter);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // --- INNER CLASSES (Model & Adapter) ---

    public static class ActivityItem {
        private String type;
        private long points;
        private String item;
        private String cashierName;
        private Timestamp ts;

        public ActivityItem() {}

        public ActivityItem(String type, long points, String item, String cashierName, Timestamp ts) {
            this.type = type;
            this.points = points;
            this.item = item;
            this.cashierName = cashierName;
            this.ts = ts;
        }

        public String getType() { return type; }
        public long getPoints() { return points; }
        public String getItem() { return item; }
        public String getCashierName() { return cashierName; }
        public Timestamp getTs() { return ts; }
    }

    private class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
        private List<ActivityItem> list;

        ActivityAdapter(List<ActivityItem> list) { this.list = list; }

        public void updateList(List<ActivityItem> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActivityItem item = list.get(position);

            String dateStr = "-";
            if (item.getTs() != null) {
                dateStr = DateFormat.format("dd MMM yyyy, HH:mm", item.getTs().toDate()).toString();
            }

            String cashier = item.getCashierName() != null && !item.getCashierName().isEmpty()
                    ? item.getCashierName()
                    : "System";

            boolean isEarn = "earn".equalsIgnoreCase(item.getType());

            if (isEarn) {
                holder.tvTitle.setText("Earned Points");
                holder.tvRedeemedItem.setVisibility(View.GONE);
                holder.tvSubtitle.setText("Processed by: " + cashier);
                holder.tvDate.setText(dateStr);

                holder.tvPoints.setText("+" + item.getPoints() + " Pts");
                holder.tvPoints.setTextColor(Color.parseColor("#10B981")); // Green

                holder.statusIndicator.setBackgroundColor(Color.parseColor("#10B981"));
                holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                holder.imgIcon.setImageResource(android.R.drawable.btn_star_big_on);

            } else {
                String rewardName = item.getItem() != null ? item.getItem() : "Reward";
                holder.tvTitle.setText("Redeemed Reward");
                holder.tvRedeemedItem.setVisibility(View.VISIBLE);
                holder.tvRedeemedItem.setText(rewardName);

                holder.tvSubtitle.setText("Processed by: " + cashier);
                holder.tvDate.setText(dateStr);

                holder.tvPoints.setText("-" + item.getPoints() + " Pts");
                holder.tvPoints.setTextColor(Color.parseColor("#EF4444")); // Red

                holder.statusIndicator.setBackgroundColor(Color.parseColor("#EF4444"));
                holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                holder.imgIcon.setImageResource(android.R.drawable.ic_menu_send);
            }
        }

        @Override
        public int getItemCount() { return list != null ? list.size() : 0; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvRedeemedItem, tvSubtitle, tvDate, tvPoints;
            ImageView imgIcon;
            FrameLayout iconContainer;
            View statusIndicator;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvHistoryTitle);
                tvRedeemedItem = v.findViewById(R.id.tvRedeemedItem);
                tvSubtitle = v.findViewById(R.id.tvHistorySubtitle);
                tvDate = v.findViewById(R.id.tvHistoryDate);
                tvPoints = v.findViewById(R.id.tvHistoryPoints);
                imgIcon = v.findViewById(R.id.imgHistoryIcon);
                iconContainer = v.findViewById(R.id.iconContainer);
                statusIndicator = v.findViewById(R.id.viewStatusIndicator);
            }
        }
    }
}