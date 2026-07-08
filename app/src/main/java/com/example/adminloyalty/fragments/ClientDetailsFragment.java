package com.example.adminloyalty.fragments;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.viewmodel.ClientDetailsViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ClientDetailsFragment extends Fragment {

    private static final String ARG_CLIENT_ID = "client_id";
    private String clientId;

    private ClientDetailsViewModel viewModel;

    // UI Views
    private TextView tvLastVisit, tvName, tvEmail, tvPhone, tvGender, tvAddress, tvPoints, tvSpend, tvEmptyHistory;
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
        tvLastVisit = view.findViewById(R.id.tvDetailLastVisit);
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

        view.findViewById(R.id.btnAdjustPoints).setOnClickListener(v -> showAdjustPointsDialog());

        // Setup ViewModel
        viewModel = new ViewModelProvider(this).get(ClientDetailsViewModel.class);
        observeViewModel();

        // Setup Filters
        setupFilters(view);

        // Load Data
        if (clientId != null) {
            viewModel.loadClientData(clientId);
        }
    }

    private void observeViewModel() {
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), document -> {
            if (document != null && document.exists()) {
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

                Timestamp lastVisit = document.getTimestamp("lastVisitTimestamp");
                String lastVisitStr = "Never";
                if (lastVisit != null) {
                    lastVisitStr = DateFormat.format("dd MMM yyyy", lastVisit.toDate()).toString();
                }
                if (tvLastVisit != null) {
                    tvLastVisit.setText(lastVisitStr);
                }

                Long points = document.getLong("points");
                tvPoints.setText(String.format(Locale.US, "%,d", points != null ? points : 0));
            }
        });

        viewModel.getAverageSpend().observe(getViewLifecycleOwner(), spend -> {
            tvSpend.setText(String.format(Locale.US, "%.2f", spend != null ? spend : 0.0));
        });

        viewModel.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            if (activities != null) {
                allActivities.clear();
                allActivities.addAll(activities);
                filterList("ALL");
            }
        });

        viewModel.getLoadingHistory().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getAdjustSuccess().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearAdjustStatus();
            }
        });
    }

    private void setupFilters(View view) {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int id = checkedIds.get(0);
            if (id == R.id.filterAll) {
                filterList("ALL");
            } else if (id == R.id.filterScans) {
                filterList("EARN");
            } else if (id == R.id.filterGifts) {
                filterList("SPEND");
            }
        });

        // ponytail: cashier filter removed — the backend activity feed carries no cashier
        // attribution, so there is nothing to filter by. Chip hidden until/if the feed adds it.
        Chip chipCashier = view.findViewById(R.id.filterCashier);
        chipCashier.setVisibility(View.GONE);

        Chip chipDate = view.findViewById(R.id.filterDate);
        chipDate.setOnClickListener(v -> {
            chipGroupFilters.check(R.id.filterDate);
            showDateRangePicker();
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

        datePicker.addOnNegativeButtonClickListener(v -> {
            chipGroupFilters.check(R.id.filterAll);
            filterList("ALL");
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void filterListByDate(long start, long end) {
        if (allActivities == null || allActivities.isEmpty()) return;

        List<ActivityItem> filtered = new ArrayList<>();
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

    private void setupRecyclerView(List<ActivityItem> items) {
        adapter = new ActivityAdapter(items);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setAdapter(adapter);
    }

    private void showAdjustPointsDialog() {
        if (clientId == null) {
            Toast.makeText(getContext(), "No client loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText etDelta = new EditText(getContext());
        etDelta.setHint("Amount (use - to deduct, e.g. -50)");
        etDelta.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(etDelta);

        EditText etReason = new EditText(getContext());
        etReason.setHint("Reason (required)");
        etReason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(etReason);

        new AlertDialog.Builder(requireContext())
                .setTitle("Adjust Points")
                .setView(box)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int delta;
                    try {
                        delta = Integer.parseInt(etDelta.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Enter a valid whole number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.adjustPoints(clientId, delta, etReason.getText().toString());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

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