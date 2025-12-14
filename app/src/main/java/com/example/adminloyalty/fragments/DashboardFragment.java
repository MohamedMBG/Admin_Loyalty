package com.example.adminloyalty.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.R;
import com.example.adminloyalty.authetification.LoginActivity;
import com.example.adminloyalty.utils.CashierRowBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private static final int MAX_CASHIERS_TO_SHOW = 5;

    // Field names in Firestore
    private static final String FIELD_EARN_DATE = "createdAt";
    private static final String FIELD_REDEEM_DATE = "createdAt";
    private static final String FIELD_USER_CREATED = "createdAt";

    private enum Period { TODAY, WEEK, MONTH }
    private Period currentPeriod = Period.TODAY;

    // UI Views
    private ChipGroup chipGroupPeriod;
    private TextView tvRevenueValue, tvRevenueDelta;
    private TextView tvRewardCostValue;
    private TextView tvVisitsValue;
    private TextView tvNewClientsValue;
    private TextView tvPointsValue;
    private TextView tvGiftsValue;
    private CardView btnOffers, allScansCard, btnRedemptions, createCashierCard, btnActionClients, giftMenu;
    private ImageView btnLogout;

    // Chart Views
    private View[] chartBars;
    private TextView[] chartValues;
    private TextView[] chartLabels;

    // Firestore
    private FirebaseFirestore db;

    // Data
    private Timestamp lastStartTs;
    private Timestamp lastEndTs;
    private LinearLayout layoutCashierList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        db = FirebaseFirestore.getInstance();
        bindViews(view);
        setupListeners();

        loadInitialData();

        return view;
    }

    private void bindViews(View view) {
        bindPeriodViews(view);
        bindMetricViews(view);
        bindChartViews(view);
        bindActionButtons(view);
        bindCashierList(view);
    }

    private void bindPeriodViews(View view) {
        chipGroupPeriod = view.findViewById(R.id.chipGroupPeriod);
    }

    private void bindMetricViews(View view) {
        tvRevenueValue = view.findViewById(R.id.tvRevenueValue);
        tvRevenueDelta = view.findViewById(R.id.tvRevenueDelta);
        tvRewardCostValue = view.findViewById(R.id.tvRewardCostValue);
        tvVisitsValue = view.findViewById(R.id.tvVisitsValue);
        tvNewClientsValue = view.findViewById(R.id.tvNewClientsValue);
        tvPointsValue = view.findViewById(R.id.tvPointsValue);
        tvGiftsValue = view.findViewById(R.id.tvGiftsValue);
    }

    private void bindChartViews(View view) {
        chartBars = new View[]{
                view.findViewById(R.id.viewChartBar1),
                view.findViewById(R.id.viewChartBar2),
                view.findViewById(R.id.viewChartBar3),
                view.findViewById(R.id.viewChartBar4),
                view.findViewById(R.id.viewChartBar5),
                view.findViewById(R.id.viewChartBar6),
                view.findViewById(R.id.viewChartBar7)
        };

        chartValues = new TextView[]{
                view.findViewById(R.id.tvChartVal1),
                view.findViewById(R.id.tvChartVal2),
                view.findViewById(R.id.tvChartVal3),
                view.findViewById(R.id.tvChartVal4),
                view.findViewById(R.id.tvChartVal5),
                view.findViewById(R.id.tvChartVal6),
                view.findViewById(R.id.tvChartVal7)
        };

        chartLabels = new TextView[]{
                view.findViewById(R.id.tvLabel1),
                view.findViewById(R.id.tvLabel2),
                view.findViewById(R.id.tvLabel3),
                view.findViewById(R.id.tvLabel4),
                view.findViewById(R.id.tvLabel5),
                view.findViewById(R.id.tvLabel6),
                view.findViewById(R.id.tvLabel7)
        };
    }

    private void bindActionButtons(View view) {
        giftMenu = view.findViewById(R.id.btnActionAddGift);
        allScansCard = view.findViewById(R.id.btnActionScans);
        btnRedemptions = view.findViewById(R.id.btnActionRedemptions);
        createCashierCard = view.findViewById(R.id.btnActionClients);
        btnActionClients = view.findViewById(R.id.btnActionView);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnOffers = view.findViewById(R.id.btnActionOffers);

        setupFragmentNavigation();
        setupLogout();
        setupExportButton(view);
    }

    private void bindCashierList(View view) {
        layoutCashierList = view.findViewById(R.id.layoutCashierList);
    }

    // MARK: - Setup Methods

    private void setupListeners() {
        chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipToday) {
                applyPeriod(Period.TODAY);
            } else if (checkedId == R.id.chipWeek) {
                applyPeriod(Period.WEEK);
            } else if (checkedId == R.id.chipMonth) {
                applyPeriod(Period.MONTH);
            }
        });
    }

    private void setupFragmentNavigation() {
        giftMenu.setOnClickListener(v -> navigateToFragment(new RewardsAdminFragment()));
        btnRedemptions.setOnClickListener(v -> navigateToFragment(new RewadLogsFragment()));
        btnActionClients.setOnClickListener(v -> navigateToFragment(new ClientsSummaryFragment()));
        allScansCard.setOnClickListener(v -> navigateToFragment(new ScanLogsFragment()));
        createCashierCard.setOnClickListener(v -> navigateToFragment(new CreateCashierFragment()));
        btnOffers.setOnClickListener(v -> navigateToFragment(new PromotionsAdminFragment()));
    }

    private void navigateToFragment(Fragment fragment) {
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void setupLogout() {
        btnLogout.setOnClickListener(view -> performLogout());
    }

    private void setupExportButton(View view) {
        View btnExport = view.findViewById(R.id.btnActionExport);
        if (btnExport != null) {
            btnExport.setOnClickListener(v ->
                    Toast.makeText(getContext(), "Exporting CSV...", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void loadInitialData() {
        applyPeriod(Period.TODAY);
    }

    // MARK: - Period Management

    private void applyPeriod(Period period) {
        currentPeriod = period;

        Date[] dateRange = getDateRange(period);
        lastStartTs = new Timestamp(dateRange[0]);
        lastEndTs = new Timestamp(dateRange[1]);

        Log.d(TAG, "Period: " + period + ", Start: " + lastStartTs.toDate() + ", End: " + lastEndTs.toDate());

        resetUI();
        loadDashboardData();
    }

    private void resetUI() {
        setTextOrDefault(tvRevenueValue, "--");
        setTextOrDefault(tvRevenueDelta, "--%");
        setTextOrDefault(tvRewardCostValue, "--");
        setTextOrDefault(tvVisitsValue, "--");
        setTextOrDefault(tvNewClientsValue, "--");
        setTextOrDefault(tvPointsValue, "--");
        setTextOrDefault(tvGiftsValue, "--");
    }

    private void setTextOrDefault(TextView textView, String defaultValue) {
        if (textView != null) {
            textView.setText(defaultValue);
        }
    }

    // MARK: - Data Loading

    private void loadDashboardData() {
        loadFinancialsAndVisits();
        loadRewardCosts();
        loadNewClients();
        loadCashierPerformance();
    }

    private void loadFinancialsAndVisits() {
        db.collection("earn_codes")
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, lastStartTs)
                .whereLessThan(FIELD_EARN_DATE, lastEndTs)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    FinancialData financialData = processFinancialData(snapshots.getDocuments());
                    updateFinancialUI(financialData);
                    loadRevenueDelta(financialData.revenue);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading financials", e);
                    if (isAdded()) {
                        Toast.makeText(getContext(),
                                "Error loading financial data", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private FinancialData processFinancialData(List<DocumentSnapshot> snapshots) {
        double totalRevenue = 0;
        long totalPoints = 0;
        Set<String> uniqueVisits = new HashSet<>();
        SimpleDateFormat visitKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        int[] chartData = new int[7];
        Arrays.fill(chartData, 0);

        for (DocumentSnapshot doc : snapshots) {
            totalRevenue += getDoubleValue(doc, "amountMAD");
            totalPoints += getLongValue(doc, "points");

            Timestamp ts = doc.getTimestamp(FIELD_EARN_DATE);
            String uid = doc.getString("redeemedByUid");

            if (ts != null && uid != null) {
                String key = uid + "_" + visitKeyFormat.format(ts.toDate());
                uniqueVisits.add(key);
                processChartData(ts, chartData);
            }
        }

        return new FinancialData(totalRevenue, totalPoints,
                uniqueVisits.size(), chartData);
    }

    private double getDoubleValue(DocumentSnapshot doc, String field) {
        Double value = doc.getDouble(field);
        return value != null ? value : 0.0;
    }

    private long getLongValue(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value != null ? value : 0L;
    }

    private void updateFinancialUI(FinancialData data) {
        tvRevenueValue.setText(formatRevenue(data.revenue));
        tvPointsValue.setText(formatPoints(data.points));
        tvVisitsValue.setText(String.valueOf(data.uniqueVisits));
        updateChartUI(data.chartData);
    }

    private String formatRevenue(double revenue) {
        return String.format(Locale.US, "%.0f", revenue);
    }

    private String formatPoints(long points) {
        if (points > 1000) {
            return String.format(Locale.US, "%.1fk", points / 1000.0);
        }
        return String.valueOf(points);
    }

    private void loadRevenueDelta(double currentRevenue) {
        if (tvRevenueDelta == null) return;

        Date[] previousRange = getPreviousPeriodRange();
        Timestamp prevStart = new Timestamp(previousRange[0]);
        Timestamp prevEnd = new Timestamp(previousRange[1]);

        db.collection("earn_codes")
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, prevStart)
                .whereLessThan(FIELD_EARN_DATE, prevEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    double previousRevenue = 0.0;
                    for (DocumentSnapshot doc : snap) {
                        previousRevenue += getDoubleValue(doc, "amountMAD");
                    }

                    String deltaText = calculateDeltaText(currentRevenue, previousRevenue);
                    tvRevenueDelta.setText(deltaText);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading revenue delta", e);
                    if (isAdded()) {
                        tvRevenueDelta.setText("--%");
                    }
                });
    }

    private String calculateDeltaText(double current, double previous) {
        if (previous <= 0) return "--%";

        double delta = ((current - previous) / previous) * 100.0;
        return String.format(Locale.US, "%.1f%%", delta);
    }

    private void loadRewardCosts() {
        db.collection("redeem_codes")
                .whereGreaterThanOrEqualTo(FIELD_REDEEM_DATE, lastStartTs)
                .whereLessThan(FIELD_REDEEM_DATE, lastEndTs)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    double totalCost = 0;
                    int count = 0;

                    for (DocumentSnapshot doc : snapshots) {
                        totalCost += getDoubleValue(doc, "costPoints");
                        count++;
                    }

                    tvRewardCostValue.setText(formatRevenue(totalCost * 0.5));
                    tvGiftsValue.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading reward costs", e);
                });
    }

    private void loadNewClients() {
        db.collection("users")
                .whereGreaterThanOrEqualTo(FIELD_USER_CREATED, lastStartTs)
                .whereLessThan(FIELD_USER_CREATED, lastEndTs)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    tvNewClientsValue.setText(String.valueOf(snap.getCount()));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading new clients", e);
                });
    }

    private void loadCashierPerformance() {
        if (layoutCashierList == null || !isAdded()) return;

        Map<String, CashierStats> statsMap = new HashMap<>();

        loadCashierScans(statsMap, () -> {
            loadCashierRedeems(statsMap, () -> {
                renderCashierRows(statsMap);
            });
        });
    }

    private void loadCashierScans(Map<String, CashierStats> statsMap, Runnable onComplete) {
        db.collection("earn_codes")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, lastStartTs)
                .whereLessThan(FIELD_EARN_DATE, lastEndTs)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        CashierInfo info = extractCashierInfo(doc, "cashierName", "createdByName");
                        updateCashierStats(statsMap, info.id, info.name).scans++;
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading cashier scans", e);
                    onComplete.run();
                });
    }

    private void loadCashierRedeems(Map<String, CashierStats> statsMap, Runnable onComplete) {
        db.collection("redeem_codes")
                .whereGreaterThanOrEqualTo(FIELD_REDEEM_DATE, lastStartTs)
                .whereLessThan(FIELD_REDEEM_DATE, lastEndTs)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        CashierInfo info = extractCashierInfo(doc, "cashierName", "processedByName");
                        updateCashierStats(statsMap, info.id, info.name).redeems++;
                    }
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading cashier redeems", e);
                    onComplete.run();
                });
    }

    private CashierInfo extractCashierInfo(DocumentSnapshot doc, String nameField, String fallbackField) {
        String id = doc.getString("cashierId");
        String name = doc.getString(nameField);

        if (id == null || id.trim().isEmpty()) {
            id = "unknown";
        }

        if (name == null || name.trim().isEmpty()) {
            name = doc.getString(fallbackField);
        }

        if (name == null || name.trim().isEmpty()) {
            name = "Unknown";
        }

        return new CashierInfo(id, name);
    }

    private CashierStats updateCashierStats(Map<String, CashierStats> statsMap, String id, String name) {
        CashierStats stats = statsMap.get(id);
        if (stats == null) {
            stats = new CashierStats(id, name);
            statsMap.put(id, stats);
        }
        return stats;
    }

    private void renderCashierRows(Map<String, CashierStats> statsMap) {
        if (layoutCashierList == null || !isAdded()) return;

        List<CashierStats> sortedCashiers = getSortedCashiers(statsMap);
        CashierRowBuilder.renderCashierRows(layoutCashierList, sortedCashiers,
                getContext(), MAX_CASHIERS_TO_SHOW);

        handleEmptyState(sortedCashiers.isEmpty());
        handleViewAllButton(sortedCashiers.size() > MAX_CASHIERS_TO_SHOW);
    }

    private List<CashierStats> getSortedCashiers(Map<String, CashierStats> statsMap) {
        List<CashierStats> list = new ArrayList<>(statsMap.values());
        Collections.sort(list, (c1, c2) ->
                Integer.compare(c2.getTotalActivity(), c1.getTotalActivity()));
        return list;
    }

    private void handleEmptyState(boolean isEmpty) {
        View layoutEmptyState = getView() != null ?
                getView().findViewById(R.id.layoutEmptyState) : null;

        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void handleViewAllButton(boolean showButton) {
        MaterialButton btnViewAll = getView() != null ?
                getView().findViewById(R.id.btnViewAllCashiers) : null;

        if (btnViewAll != null) {
            btnViewAll.setVisibility(showButton ? View.VISIBLE : View.GONE);
            if (showButton) {
                btnViewAll.setOnClickListener(v -> showAllCashiers());
            }
        }
    }

    // MARK: - Chart Logic

    private void processChartData(Timestamp ts, int[] data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());
        int index = -1;

        switch (currentPeriod) {
            case TODAY:
                index = getHourIndex(cal.get(Calendar.HOUR_OF_DAY));
                break;
            case WEEK:
                index = getWeekdayIndex(cal.get(Calendar.DAY_OF_WEEK));
                break;
            case MONTH:
                index = getMonthDayIndex(cal.get(Calendar.DAY_OF_MONTH));
                break;
        }

        if (index >= 0 && index < 7) {
            data[index]++;
        }
    }

    private int getHourIndex(int hour) {
        if (hour >= 8) {
            int index = (hour - 8) / 2;
            return Math.min(index, 6);
        }
        return -1;
    }

    private int getWeekdayIndex(int dayOfWeek) {
        return (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
    }

    private int getMonthDayIndex(int dayOfMonth) {
        int index = dayOfMonth / 5;
        return Math.min(index, 6);
    }

    private void updateChartUI(int[] values) {
        if (!isAdded() || chartBars == null || chartBars[0] == null) return;

        int max = Arrays.stream(values).max().orElse(1);
        String[] labels = getChartLabels();

        for (int i = 0; i < 7; i++) {
            if (chartValues[i] != null) {
                chartValues[i].setText(String.valueOf(values[i]));
            }

            if (chartLabels[i] != null) {
                chartLabels[i].setText(labels[i]);
            }

            if (chartBars[i] != null) {
                updateChartBarHeight(chartBars[i], values[i], max);
            }
        }
    }

    private String[] getChartLabels() {
        switch (currentPeriod) {
            case TODAY:
                return new String[]{"8", "10", "12", "14", "16", "18", "20+"};
            case WEEK:
                return new String[]{"M", "T", "W", "T", "F", "S", "S"};
            case MONTH:
                return new String[]{"J1-5", "J6-10", "J11-15", "J16-20", "J21-25", "J26-30", "J31+"};
            default:
                return new String[7];
        }
    }

    private void updateChartBarHeight(View barView, int value, int max) {
        float percentage = (float) value / max;
        int heightDp = (int) (110 * percentage);
        heightDp = Math.max(heightDp, 5);

        ViewGroup.LayoutParams params = barView.getLayoutParams();
        float density = getResources().getDisplayMetrics().density;
        params.height = (int) (heightDp * density + 0.5f);
        barView.setLayoutParams(params);
    }

    // MARK: - Utility Methods

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private Date[] getDateRange(Period period) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        switch (period) {
            case TODAY:
                end.setTime(start.getTime());
                end.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case WEEK:
                start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                end.setTime(start.getTime());
                end.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                end.setTime(start.getTime());
                end.add(Calendar.MONTH, 1);
                break;
        }

        return new Date[]{start.getTime(), end.getTime()};
    }

    private Date[] getPreviousPeriodRange() {
        Date startDate = lastStartTs.toDate();
        Date endDate = lastEndTs.toDate();
        long durationMs = endDate.getTime() - startDate.getTime();

        Date prevEndDate = startDate;
        Date prevStartDate = new Date(prevEndDate.getTime() - durationMs);

        return new Date[]{prevStartDate, prevEndDate};
    }

    private void showAllCashiers() {
        Toast.makeText(getContext(), "Showing full staff list", Toast.LENGTH_SHORT).show();
    }

    // MARK: - Data Classes

    private static class FinancialData {
        double revenue;
        long points;
        int uniqueVisits;
        int[] chartData;

        FinancialData(double revenue, long points, int uniqueVisits, int[] chartData) {
            this.revenue = revenue;
            this.points = points;
            this.uniqueVisits = uniqueVisits;
            this.chartData = chartData;
        }
    }

    private static class CashierInfo {
        String id;
        String name;

        CashierInfo(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class CashierStats {
        public String id;
        public String name;
        public int scans;
        public int redeems;

        CashierStats(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getTotalActivity() {
            return scans + redeems;
        }
    }
}