package com.example.adminloyalty.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.authetification.LoginActivity;
import com.example.adminloyalty.data.DashboardRepository.CashierStats;
import com.example.adminloyalty.data.DashboardRepository.DashboardPeriod;
import com.example.adminloyalty.utils.CashierRowBuilder;
import com.example.adminloyalty.viewmodel.DashboardViewModel;
import com.example.adminloyalty.viewmodel.DashboardViewModel.DashboardUiState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
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

    private ChipGroup chipGroupPeriod;

    private TextView tvRevenueValue, tvRevenueDelta;
    private TextView tvRewardCostValue;
    private TextView tvVisitsValue;
    private TextView tvNewClientsValue;
    private TextView tvPointsValue;
    private TextView tvGiftsValue;

    private CardView btnOffers, allScansCard, btnRedemptions, createCashierCard, btnActionClients, giftMenu;
    private ImageView btnLogout;

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
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        density = getResources().getDisplayMetrics().density;

        bindViews(view);
        setupListeners();
        setupNavigation();
        setupLogout();
        setupExport(view);
        setupBack();
        setupViewModel();

        viewModel.selectPeriod(DashboardPeriod.TODAY);

        return view;
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

    private void bindViews(@NonNull View view) {
        chipGroupPeriod = view.findViewById(R.id.chipGroupPeriod);

        tvRevenueValue = view.findViewById(R.id.tvRevenueValue);
        tvRevenueDelta = view.findViewById(R.id.tvRevenueDelta);
        tvRewardCostValue = view.findViewById(R.id.tvRewardCostValue);
        tvVisitsValue = view.findViewById(R.id.tvVisitsValue);
        tvNewClientsValue = view.findViewById(R.id.tvNewClientsValue);
        tvPointsValue = view.findViewById(R.id.tvPointsValue);
        tvGiftsValue = view.findViewById(R.id.tvGiftsValue);

        chartBars[0] = view.findViewById(R.id.viewChartBar1);
        chartBars[1] = view.findViewById(R.id.viewChartBar2);
        chartBars[2] = view.findViewById(R.id.viewChartBar3);
        chartBars[3] = view.findViewById(R.id.viewChartBar4);
        chartBars[4] = view.findViewById(R.id.viewChartBar5);
        chartBars[5] = view.findViewById(R.id.viewChartBar6);
        chartBars[6] = view.findViewById(R.id.viewChartBar7);

        chartValues[0] = view.findViewById(R.id.tvChartVal1);
        chartValues[1] = view.findViewById(R.id.tvChartVal2);
        chartValues[2] = view.findViewById(R.id.tvChartVal3);
        chartValues[3] = view.findViewById(R.id.tvChartVal4);
        chartValues[4] = view.findViewById(R.id.tvChartVal5);
        chartValues[5] = view.findViewById(R.id.tvChartVal6);
        chartValues[6] = view.findViewById(R.id.tvChartVal7);

        chartLabels[0] = view.findViewById(R.id.tvLabel1);
        chartLabels[1] = view.findViewById(R.id.tvLabel2);
        chartLabels[2] = view.findViewById(R.id.tvLabel3);
        chartLabels[3] = view.findViewById(R.id.tvLabel4);
        chartLabels[4] = view.findViewById(R.id.tvLabel5);
        chartLabels[5] = view.findViewById(R.id.tvLabel6);
        chartLabels[6] = view.findViewById(R.id.tvLabel7);

        giftMenu = view.findViewById(R.id.btnActionAddGift);
        allScansCard = view.findViewById(R.id.btnActionScans);
        btnRedemptions = view.findViewById(R.id.btnActionRedemptions);
        createCashierCard = view.findViewById(R.id.btnActionClients);
        btnActionClients = view.findViewById(R.id.btnActionView);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnOffers = view.findViewById(R.id.btnActionOffers);

        layoutCashierList = view.findViewById(R.id.layoutCashierList);
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
        chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipToday) applyPeriod(DashboardPeriod.TODAY);
            else if (checkedId == R.id.chipWeek) applyPeriod(DashboardPeriod.WEEK);
            else if (checkedId == R.id.chipMonth) applyPeriod(DashboardPeriod.MONTH);
        });
    }

    private void setupNavigation() {
        giftMenu.setOnClickListener(v -> navigateToFragment(new RewardsAdminFragment()));
        btnRedemptions.setOnClickListener(v -> navigateToFragment(new RewadLogsFragment()));
        btnActionClients.setOnClickListener(v -> navigateToFragment(new ClientsSummaryFragment()));
        allScansCard.setOnClickListener(v -> navigateToFragment(new ScanLogsFragment()));
        createCashierCard.setOnClickListener(v -> navigateToFragment(new CreateCashierFragment()));
        btnOffers.setOnClickListener(v -> navigateToFragment(new PromotionsAdminFragment()));
    }

    private void setupLogout() {
        btnLogout.setOnClickListener(v -> performLogout());
    }

    private void setupExport(@NonNull View root) {
        View btnExport = root.findViewById(R.id.btnActionExport);
        if (btnExport != null) {
            btnExport.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Exporting CSV...", Toast.LENGTH_SHORT).show()
            );
        }
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
        setText(tvRevenueValue, "--");
        setText(tvRevenueDelta, "--%");
        setText(tvRewardCostValue, "--");
        setText(tvVisitsValue, "--");
        setText(tvNewClientsValue, "--");
        setText(tvPointsValue, "--");
        setText(tvGiftsValue, "--");

        int[] zeros = new int[CHART_SIZE];
        updateChartUI(zeros);
    }

    private void renderState(@Nullable DashboardUiState state) {
        if (state == null || !isAdded()) return;
        currentPeriod = state.period;

        tvRevenueValue.setText(formatMoney0(state.revenue));
        tvRevenueDelta.setText(formatDeltaPercent(state.revenue, state.previousRevenue));
        tvRewardCostValue.setText(formatMoney0(state.totalCostPoints * 0.5));
        tvVisitsValue.setText(String.valueOf(state.uniqueVisits));
        tvNewClientsValue.setText(String.valueOf(state.newClients));
        tvPointsValue.setText(formatPoints(state.points));
        tvGiftsValue.setText(String.valueOf(state.gifts));

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
        View root = getView();
        if (root == null) return;

        View emptyState = root.findViewById(R.id.layoutEmptyState);
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void handleViewAllButton(boolean show) {
        View root = getView();
        if (root == null) return;

        MaterialButton btnViewAll = root.findViewById(R.id.btnViewAllCashiers);
        if (btnViewAll == null) return;

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
