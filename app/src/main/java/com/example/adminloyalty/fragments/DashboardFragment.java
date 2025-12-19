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

import androidx.activity.OnBackPressedCallback;
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
import com.google.firebase.firestore.QuerySnapshot;

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

/**
 * Clean code / perf improvements:
 * - Avoids fetching whole collections just to count (uses count() / aggregates where possible).
 * - Avoids multiple passes on data, reduces allocations (reuses arrays, avoids streams).
 * - Safer lifecycle (guards UI updates if fragment detached).
 * - Extracted constants, helpers, and clearer responsibility split.
 * - Executes related async calls in parallel and updates UI once.
 */
public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";

    private static final int MAX_CASHIERS_TO_SHOW = 5;
    private static final int CHART_SIZE = 7;
    private static final int MIN_BAR_DP = 5;
    private static final int MAX_BAR_DP = 110;

    // Collections
    private static final String COL_EARN = "earn_codes";
    private static final String COL_REDEEM = "redeem_codes";
    private static final String COL_USERS = "users";

    // Common fields
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_REDEEMED = "redeemed";

    // Earn fields
    private static final String FIELD_AMOUNT_MAD = "amountMAD";
    private static final String FIELD_POINTS = "points";
    private static final String FIELD_REDEEMED_BY_UID = "redeemedByUid";
    private static final String FIELD_CASHIER_ID = "cashierId";
    private static final String FIELD_CASHIER_NAME = "cashierName";
    private static final String FIELD_CREATED_BY_NAME = "createdByName";

    // Redeem fields
    private static final String FIELD_COST_POINTS = "costPoints";
    private static final String FIELD_PROCESSED_BY_NAME = "processedByName";

    private enum Period { TODAY, WEEK, MONTH }

    private Period currentPeriod = Period.TODAY;

    // UI
    private ChipGroup chipGroupPeriod;

    private TextView tvRevenueValue, tvRevenueDelta;
    private TextView tvRewardCostValue;
    private TextView tvVisitsValue;
    private TextView tvNewClientsValue;
    private TextView tvPointsValue;
    private TextView tvGiftsValue;

    private CardView btnOffers, allScansCard, btnRedemptions, createCashierCard, btnActionClients, giftMenu;
    private ImageView btnLogout;

    // Chart
    private final View[] chartBars = new View[CHART_SIZE];
    private final TextView[] chartValues = new TextView[CHART_SIZE];
    private final TextView[] chartLabels = new TextView[CHART_SIZE];
    private final int[] chartDataBuffer = new int[CHART_SIZE]; // reused to avoid allocations

    private LinearLayout layoutCashierList;

    // Firestore
    private FirebaseFirestore db;

    // Date range (current)
    private Timestamp rangeStart;
    private Timestamp rangeEnd;

    // Small perf: cached density to avoid repeated calls
    private float density = 1f;

    // Tracks latest request to avoid showing stale results after rapid chip changes
    private long requestToken = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        db = FirebaseFirestore.getInstance();
        density = getResources().getDisplayMetrics().density;

        bindViews(view);
        setupListeners();
        applyPeriod(Period.TODAY);

        return view;
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

        // Chart views
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

        // Actions
        giftMenu = view.findViewById(R.id.btnActionAddGift);
        allScansCard = view.findViewById(R.id.btnActionScans);
        btnRedemptions = view.findViewById(R.id.btnActionRedemptions);
        createCashierCard = view.findViewById(R.id.btnActionClients);
        btnActionClients = view.findViewById(R.id.btnActionView);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnOffers = view.findViewById(R.id.btnActionOffers);

        layoutCashierList = view.findViewById(R.id.layoutCashierList);

        setupNavigation();
        setupLogout();
        setupExport(view);
        setupBack(view);
    }

    private void setupBack(@NonNull View view) {
        // Optional: better back handling inside dashboard if needed
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // default behavior
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                }
        );
    }

    private void setupListeners() {
        chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipToday) applyPeriod(Period.TODAY);
            else if (checkedId == R.id.chipWeek) applyPeriod(Period.WEEK);
            else if (checkedId == R.id.chipMonth) applyPeriod(Period.MONTH);
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

    // ---------------- Period & Range ----------------

    private void applyPeriod(@NonNull Period period) {
        currentPeriod = period;
        requestToken++; // invalidate previous results
        long token = requestToken;

        DateRange range = DateRange.forPeriod(period);
        rangeStart = new Timestamp(range.start);
        rangeEnd = new Timestamp(range.end);

        resetUI();
        loadDashboardData(token);
    }

    private void resetUI() {
        setText(tvRevenueValue, "--");
        setText(tvRevenueDelta, "--%");
        setText(tvRewardCostValue, "--");
        setText(tvVisitsValue, "--");
        setText(tvNewClientsValue, "--");
        setText(tvPointsValue, "--");
        setText(tvGiftsValue, "--");

        Arrays.fill(chartDataBuffer, 0);
        updateChartUI(chartDataBuffer);
    }

    private void setText(@Nullable TextView tv, @NonNull String value) {
        if (tv != null) tv.setText(value);
    }

    // ---------------- Data Loading ----------------

    private void loadDashboardData(long token) {
        // These can run in parallel
        loadFinancialsAndVisits(token);
        loadRewardCosts(token);
        loadNewClientsCount(token);
        loadCashierPerformance(token);
    }

    /**
     * Uses ONE query for current period financials.
     * Delta requires previous period; still a second query, but kept minimal.
     */
    private void loadFinancialsAndVisits(long token) {
        db.collection(COL_EARN)
                .whereEqualTo(FIELD_STATUS, STATUS_REDEEMED)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, rangeStart)
                .whereLessThan(FIELD_CREATED_AT, rangeEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || token != requestToken) return;

                    FinancialData data = processFinancialData(snap);
                    renderFinancials(data);

                    // Revenue delta is computed from previous range
                    loadRevenueDelta(token, data.revenue);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Financials error", e);
                    if (isAdded() && token == requestToken) {
                        Toast.makeText(getContext(), "Error loading financial data", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private FinancialData processFinancialData(@NonNull QuerySnapshot snap) {
        double revenue = 0.0;
        long points = 0L;

        // unique visit: (uid + dayKey). HashSet is ok for small/medium; keep it local.
        Set<String> uniqueVisits = new HashSet<>();
        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

        Arrays.fill(chartDataBuffer, 0);

        for (DocumentSnapshot doc : snap.getDocuments()) {
            revenue += safeDouble(doc, FIELD_AMOUNT_MAD);
            points += safeLong(doc, FIELD_POINTS);

            Timestamp ts = doc.getTimestamp(FIELD_CREATED_AT);
            String uid = doc.getString(FIELD_REDEEMED_BY_UID);

            if (ts != null && uid != null && !uid.trim().isEmpty()) {
                uniqueVisits.add(uid + "_" + dayKeyFormat.format(ts.toDate()));
                incrementChartBucket(ts, chartDataBuffer);
            }
        }

        return new FinancialData(revenue, points, uniqueVisits.size(), copyChart(chartDataBuffer));
    }

    private void renderFinancials(@NonNull FinancialData data) {
        tvRevenueValue.setText(formatMoney0(data.revenue));
        tvPointsValue.setText(formatPoints(data.points));
        tvVisitsValue.setText(String.valueOf(data.uniqueVisits));
        updateChartUI(data.chartData);
    }

    private void loadRevenueDelta(long token, double currentRevenue) {
        DateRange prev = DateRange.previousOf(rangeStart.toDate(), rangeEnd.toDate());

        db.collection(COL_EARN)
                .whereEqualTo(FIELD_STATUS, STATUS_REDEEMED)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, new Timestamp(prev.start))
                .whereLessThan(FIELD_CREATED_AT, new Timestamp(prev.end))
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || token != requestToken) return;

                    double prevRevenue = 0.0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        prevRevenue += safeDouble(doc, FIELD_AMOUNT_MAD);
                    }

                    tvRevenueDelta.setText(formatDeltaPercent(currentRevenue, prevRevenue));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Revenue delta error", e);
                    if (isAdded() && token == requestToken) tvRevenueDelta.setText("--%");
                });
    }

    /**
     * Reward costs: you were reading all docs & summing. If your backend supports aggregates
     * you can do server-side sum using Cloud Functions; Firestore doesn't do SUM aggregate yet
     * (count exists). So we keep the read but keep it lean.
     */
    private void loadRewardCosts(long token) {
        db.collection(COL_REDEEM)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, rangeStart)
                .whereLessThan(FIELD_CREATED_AT, rangeEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || token != requestToken) return;

                    double totalCostPoints = 0.0;
                    int gifts = snap.size();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        totalCostPoints += safeDouble(doc, FIELD_COST_POINTS);
                    }

                    // Kept your rule: cost * 0.5 (assuming points -> MAD conversion)
                    tvRewardCostValue.setText(formatMoney0(totalCostPoints * 0.5));
                    tvGiftsValue.setText(String.valueOf(gifts));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Reward costs error", e));
    }

    private void loadNewClientsCount(long token) {
        db.collection(COL_USERS)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, rangeStart)
                .whereLessThan(FIELD_CREATED_AT, rangeEnd)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(agg -> {
                    if (!isAdded() || token != requestToken) return;
                    tvNewClientsValue.setText(String.valueOf(agg.getCount()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "New clients count error", e));
    }

    /**
     * Cashier performance:
     * - Uses only 2 queries (earn + redeem) and aggregates in-memory (same as your original)
     * - Avoids nested callbacks by using a small coordination method (no extra libs).
     */
    private void loadCashierPerformance(long token) {
        if (!isAdded() || layoutCashierList == null) return;

        Map<String, CashierStats> stats = new HashMap<>();
        PendingTasks pending = new PendingTasks(2, () -> {
            if (!isAdded() || token != requestToken) return;
            renderCashierRows(stats);
        });

        // Earn scans
        db.collection(COL_EARN)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, rangeStart)
                .whereLessThan(FIELD_CREATED_AT, rangeEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        CashierInfo info = extractCashierInfo(doc, FIELD_CASHIER_NAME, FIELD_CREATED_BY_NAME);
                        statsFor(stats, info).scans++;
                    }
                    pending.done();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cashier scans error", e);
                    pending.done();
                });

        // Redeems
        db.collection(COL_REDEEM)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, rangeStart)
                .whereLessThan(FIELD_CREATED_AT, rangeEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        CashierInfo info = extractCashierInfo(doc, FIELD_CASHIER_NAME, FIELD_PROCESSED_BY_NAME);
                        statsFor(stats, info).redeems++;
                    }
                    pending.done();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cashier redeems error", e);
                    pending.done();
                });
    }

    private void renderCashierRows(@NonNull Map<String, CashierStats> statsMap) {
        if (!isAdded() || layoutCashierList == null) return;

        List<CashierStats> list = new ArrayList<>(statsMap.values());
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

    // ---------------- Chart ----------------

    private void incrementChartBucket(@NonNull Timestamp ts, @NonNull int[] data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());

        int index;
        switch (currentPeriod) {
            case TODAY:
                index = hourBucket(cal.get(Calendar.HOUR_OF_DAY));
                break;
            case WEEK:
                index = weekdayBucket(cal.get(Calendar.DAY_OF_WEEK));
                break;
            case MONTH:
                index = monthBucket(cal.get(Calendar.DAY_OF_MONTH));
                break;
            default:
                index = -1;
        }

        if (index >= 0 && index < CHART_SIZE) data[index]++;
    }

    private int hourBucket(int hour) {
        if (hour < 8) return -1;
        int index = (hour - 8) / 2;
        return Math.min(index, CHART_SIZE - 1);
    }

    private int weekdayBucket(int dayOfWeek) {
        // Monday..Sunday -> 0..6 (Sunday becomes 6)
        return (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
    }

    private int monthBucket(int dayOfMonth) {
        int index = dayOfMonth / 5;
        return Math.min(index, CHART_SIZE - 1);
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

    private String[] chartLabelsFor(@NonNull Period period) {
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

    // ---------------- Helpers / Formatting ----------------

    private static double safeDouble(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Double v = doc.getDouble(field);
        return v != null ? v : 0.0;
    }

    private static long safeLong(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Long v = doc.getLong(field);
        return v != null ? v : 0L;
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

    private CashierInfo extractCashierInfo(@NonNull DocumentSnapshot doc,
                                           @NonNull String nameField,
                                           @NonNull String fallbackNameField) {
        String id = trimOrNull(doc.getString(FIELD_CASHIER_ID));
        if (id == null) id = "unknown";

        String name = trimOrNull(doc.getString(nameField));
        if (name == null) name = trimOrNull(doc.getString(fallbackNameField));
        if (name == null) name = "Unknown";

        return new CashierInfo(id, name);
    }

    private static CashierStats statsFor(@NonNull Map<String, CashierStats> map, @NonNull CashierInfo info) {
        CashierStats st = map.get(info.id);
        if (st == null) {
            st = new CashierStats(info.id, info.name);
            map.put(info.id, st);
        }
        return st;
    }

    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int[] copyChart(@NonNull int[] src) {
        // return a copy so the UI holds stable data even if buffer reused later
        return Arrays.copyOf(src, src.length);
    }

    // ---------------- Logout / Misc ----------------

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

    // ---------------- Small internal classes ----------------

    private static final class FinancialData {
        final double revenue;
        final long points;
        final int uniqueVisits;
        final int[] chartData;

        FinancialData(double revenue, long points, int uniqueVisits, int[] chartData) {
            this.revenue = revenue;
            this.points = points;
            this.uniqueVisits = uniqueVisits;
            this.chartData = chartData;
        }
    }

    private static final class CashierInfo {
        final String id;
        final String name;

        CashierInfo(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class CashierStats {
        public final String id;
        public final String name;
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

    /**
     * Tiny coordinator to avoid deep callback nesting without adding Tasks.whenAllSuccess.
     */
    private static final class PendingTasks {
        private int remaining;
        private final Runnable onAllDone;

        PendingTasks(int count, @NonNull Runnable onAllDone) {
            this.remaining = count;
            this.onAllDone = onAllDone;
        }

        void done() {
            remaining--;
            if (remaining <= 0) onAllDone.run();
        }
    }

    /**
     * Date range value object (start inclusive, end exclusive).
     */
    private static final class DateRange {
        final Date start;
        final Date end;

        DateRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }

        static DateRange forPeriod(@NonNull Period p) {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();

            // start of day
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);

            switch (p) {
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

                default:
                    end.setTime(start.getTime());
                    end.add(Calendar.DAY_OF_YEAR, 1);
                    break;
            }

            return new DateRange(start.getTime(), end.getTime());
        }

        static DateRange previousOf(@NonNull Date currentStart, @NonNull Date currentEnd) {
            long duration = currentEnd.getTime() - currentStart.getTime();
            Date prevEnd = currentStart;
            Date prevStart = new Date(prevEnd.getTime() - duration);
            return new DateRange(prevStart, prevEnd);
        }
    }
}
