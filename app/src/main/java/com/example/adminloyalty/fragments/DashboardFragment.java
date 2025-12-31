package com.example.adminloyalty.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.authetification.LoginActivity;
import com.example.adminloyalty.data.DashboardRepository.CashierStats;
import com.example.adminloyalty.data.DashboardRepository.DashboardPeriod;
import com.example.adminloyalty.databinding.FragmentDashboardBinding;
import com.example.adminloyalty.utils.CashierRowBuilder;
import com.example.adminloyalty.viewmodel.DashboardViewModel;
import com.example.adminloyalty.viewmodel.DashboardViewModel.DashboardUiState;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";

    private static final int MAX_CASHIERS_TO_SHOW = 5;
    private static final int CHART_SIZE = 7;
    private static final int MIN_BAR_DP = 5;
    private static final int MAX_BAR_DP = 110;

    private DashboardViewModel viewModel;
    private DashboardPeriod currentPeriod = DashboardPeriod.TODAY;

    private FragmentDashboardBinding binding;

    private final View[] chartBars = new View[CHART_SIZE];
    private final TextView[] chartValues = new TextView[CHART_SIZE];
    private final TextView[] chartLabels = new TextView[CHART_SIZE];

    private LinearLayout layoutCashierList;

    private float density = 1f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);

        density = getResources().getDisplayMetrics().density;

        bindViews();
        setupListeners();
        setupNavigation();
        setupLogout();
        setupExport();
        setupBack();
        setupViewModel();

        viewModel.selectPeriod(DashboardPeriod.TODAY);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading) {
                resetUI();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && isAdded()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindViews() {
        if (binding == null) return;

        chartBars[0] = binding.viewChartBar1;
        chartBars[1] = binding.viewChartBar2;
        chartBars[2] = binding.viewChartBar3;
        chartBars[3] = binding.viewChartBar4;
        chartBars[4] = binding.viewChartBar5;
        chartBars[5] = binding.viewChartBar6;
        chartBars[6] = binding.viewChartBar7;

        chartValues[0] = binding.tvChartVal1;
        chartValues[1] = binding.tvChartVal2;
        chartValues[2] = binding.tvChartVal3;
        chartValues[3] = binding.tvChartVal4;
        chartValues[4] = binding.tvChartVal5;
        chartValues[5] = binding.tvChartVal6;
        chartValues[6] = binding.tvChartVal7;

        chartLabels[0] = binding.tvLabel1;
        chartLabels[1] = binding.tvLabel2;
        chartLabels[2] = binding.tvLabel3;
        chartLabels[3] = binding.tvLabel4;
        chartLabels[4] = binding.tvLabel5;
        chartLabels[5] = binding.tvLabel6;
        chartLabels[6] = binding.tvLabel7;

        layoutCashierList = binding.layoutCashierList;
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
    }

    private void setupListeners() {
        if (binding == null) return;
        binding.chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipToday) applyPeriod(DashboardPeriod.TODAY);
            else if (checkedId == R.id.chipWeek) applyPeriod(DashboardPeriod.WEEK);
            else if (checkedId == R.id.chipMonth) applyPeriod(DashboardPeriod.MONTH);
        });
    }

    private void setupNavigation() {
        if (binding == null) return;
        binding.btnActionAddGift.setOnClickListener(v -> navigateToFragment(new RewardsAdminFragment()));
        binding.btnActionRedemptions.setOnClickListener(v -> navigateToFragment(new RewadLogsFragment()));
        binding.btnActionView.setOnClickListener(v -> navigateToFragment(new ClientsSummaryFragment()));
        binding.btnActionScans.setOnClickListener(v -> navigateToFragment(new ScanLogsFragment()));
        binding.btnActionClients.setOnClickListener(v -> navigateToFragment(new CreateCashierFragment()));
        binding.btnActionOffers.setOnClickListener(v -> navigateToFragment(new PromotionsAdminFragment()));
    }

    private void setupLogout() {
        if (binding == null) return;
        binding.btnLogout.setOnClickListener(v -> performLogout());
    }

    private void setupExport() {
        if (binding == null) return;
        View btnExport = binding.btnActionExport;
        btnExport.setOnClickListener(v ->
                Toast.makeText(getContext(), "Exporting CSV...", Toast.LENGTH_SHORT).show()
        );
    }

    private void navigateToFragment(@NonNull Fragment fragment) {
        if (!isAdded()) return;
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void applyPeriod(@NonNull DashboardPeriod period) {
        currentPeriod = period;
        resetUI();
        viewModel.selectPeriod(period);
    }

    private void resetUI() {
        if (binding != null) {
            setText(binding.tvRevenueValue, "--");
            setText(binding.tvRevenueDelta, "--%");
            setText(binding.tvRewardCostValue, "--");
            setText(binding.tvVisitsValue, "--");
            setText(binding.tvNewClientsValue, "--");
            setText(binding.tvPointsValue, "--");
            setText(binding.tvGiftsValue, "--");
        }

        int[] zeros = new int[CHART_SIZE];
        updateChartUI(zeros);
    }

    private void renderState(@Nullable DashboardUiState state) {
        if (state == null || !isAdded()) return;
        currentPeriod = state.period;

        binding.tvRevenueValue.setText(formatMoney0(state.revenue));
        binding.tvRevenueDelta.setText(formatDeltaPercent(state.revenue, state.previousRevenue));
        binding.tvRewardCostValue.setText(formatMoney0(state.totalCostPoints * 0.5));
        binding.tvVisitsValue.setText(String.valueOf(state.uniqueVisits));
        binding.tvNewClientsValue.setText(String.valueOf(state.newClients));
        binding.tvPointsValue.setText(formatPoints(state.points));
        binding.tvGiftsValue.setText(String.valueOf(state.gifts));

        updateChartUI(state.chartData);
        renderCashierRows(state.cashiers);
    }

    private void renderCashierRows(@NonNull List<CashierStats> statsList) {
        if (!isAdded() || layoutCashierList == null) return;

        List<CashierStats> list = statsList;
        if (!(statsList instanceof java.util.RandomAccess)) {
            list = Arrays.asList(statsList.toArray(new CashierStats[0]));
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.getTotalActivity(), a.getTotalActivity()));

        CashierRowBuilder.renderCashierRows(layoutCashierList, list, getContext(), MAX_CASHIERS_TO_SHOW);

        handleEmptyState(list.isEmpty());
        handleViewAllButton(list.size() > MAX_CASHIERS_TO_SHOW);
    }

    private void handleEmptyState(boolean empty) {
        if (binding == null) return;

        View emptyState = binding.layoutEmptyState;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void handleViewAllButton(boolean show) {
        if (binding == null) return;

        MaterialButton btnViewAll = binding.btnViewAllCashiers;

        btnViewAll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) btnViewAll.setOnClickListener(v -> showAllCashiers());
        else btnViewAll.setOnClickListener(null);
    }

    private void updateChartUI(@NonNull int[] values) {
        if (!isAdded()) return;

        int max = 1;
        for (int v : values) if (v > max) max = v;

        String[] labels = chartLabelsFor(currentPeriod);

        for (int i = 0; i < CHART_SIZE; i++) {
            if (chartValues[i] != null) chartValues[i].setText(String.valueOf(values[i]));
            if (chartLabels[i] != null) chartLabels[i].setText(labels[i]);
            if (chartBars[i] != null) setBarHeight(chartBars[i], values[i], max);
        }
    }

    private String[] chartLabelsFor(@NonNull DashboardPeriod period) {
        switch (period) {
            case TODAY:
                return new String[]{"8", "10", "12", "14", "16", "18", "20+"};
            case WEEK:
                return new String[]{"M", "T", "W", "T", "F", "S", "S"};
            case MONTH:
                return new String[]{"J1-5", "J6-10", "J11-15", "J16-20", "J21-25", "J26-30", "J31+"};
            default:
                return new String[CHART_SIZE];
        }
    }

    private void setBarHeight(@NonNull View bar, int value, int max) {
        float pct = (max <= 0) ? 0f : (float) value / (float) max;
        int dp = (int) (MAX_BAR_DP * pct);
        dp = Math.max(dp, MIN_BAR_DP);

        ViewGroup.LayoutParams params = bar.getLayoutParams();
        params.height = (int) (dp * density + 0.5f);
        bar.setLayoutParams(params);
    }

    private static void setText(@Nullable TextView tv, @NonNull String value) {
        if (tv != null) tv.setText(value);
    }

    private static String formatMoney0(double amount) {
        return String.format(Locale.US, "%.0f", amount);
    }

    private static String formatPoints(long points) {
        if (points >= 1000) return String.format(Locale.US, "%.1fk", points / 1000.0);
        return String.valueOf(points);
    }

    private static String formatDeltaPercent(double current, double previous) {
        if (previous <= 0) return "--%";
        double delta = ((current - previous) / previous) * 100.0;
        return String.format(Locale.US, "%.1f%%", delta);
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        if (getActivity() != null) getActivity().finish();
    }

    private void showAllCashiers() {
        Toast.makeText(getContext(), "Showing full staff list", Toast.LENGTH_SHORT).show();
    }
}
