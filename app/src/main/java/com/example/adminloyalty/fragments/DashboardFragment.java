package com.example.adminloyalty.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DashboardFragment extends Fragment {

    private static final String TAG = "Dashboard";

    // 🔧 SET THESE TO MATCH YOUR SCHEMA 🔧
    private static final String FIELD_EARN_DATE = "createdAt";     // or "redeemedAt"
    private static final String FIELD_REDEEM_DATE = "createdAt";   // or "redeemedAt"
    private static final String FIELD_USER_CREATED = "createdAt";  // users createdAt

    // --- Period Enum for Logic ---
    private enum Period { TODAY, WEEK, MONTH }
    private Period currentPeriod = Period.TODAY;

    // --- UI Views ---
    private ChipGroup chipGroupPeriod;
    private TextView tvRevenueValue, tvRevenueDelta;
    private TextView tvRewardCostValue;
    private TextView tvVisitsValue;
    private TextView tvNewClientsValue;
    private TextView tvPointsValue;
    private TextView tvGiftsValue;
    private CardView cardAlert, allScansCard, btnRedemptions, createCashierCard,btnActionClients;
    private TextView tvAlertMessage;
    private ImageView btnLogout;

    // Chart Views
    private View[] chartBars;
    private TextView[] chartValues;
    private TextView[] chartLabels;

    // Firestore
    private FirebaseFirestore db;

    // Keep last period range to compute revenue delta
    private Timestamp lastStartTs;
    private Timestamp lastEndTs;

    private LinearLayout layoutCashierList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);

        db = FirebaseFirestore.getInstance();

        bindViews(v);
        setupListeners();

        // Initial Data Load
        applyPeriod(Period.TODAY);

        return v;
    }
    private void performLogout() {
        // 1. Sign out from Firebase
        FirebaseAuth.getInstance().signOut();

        // 2. Redirect to Login Activity and clear back stack
        // Assuming your login class is named LoginActivity
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // 3. Close current fragment/activity if needed
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
    // -------------------------------------------------------------------------
    // BINDING
    // -------------------------------------------------------------------------
    private void bindViews(View v) {
        chipGroupPeriod = v.findViewById(R.id.chipGroupPeriod);

        // Metric Cards
        tvRevenueValue = v.findViewById(R.id.tvRevenueValue);
        tvRevenueDelta = v.findViewById(R.id.tvRevenueDelta);
        tvRewardCostValue = v.findViewById(R.id.tvRewardCostValue);
        tvVisitsValue = v.findViewById(R.id.tvVisitsValue);
        tvNewClientsValue = v.findViewById(R.id.tvNewClientsValue);
        tvPointsValue = v.findViewById(R.id.tvPointsValue);
        tvGiftsValue = v.findViewById(R.id.tvGiftsValue);
        allScansCard = v.findViewById(R.id.btnActionScans);
        btnRedemptions = v.findViewById(R.id.btnActionRedemptions);
        createCashierCard = v.findViewById(R.id.btnActionClients);
        btnActionClients = v.findViewById(R.id.btnActionView);
        btnLogout = v.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(view -> performLogout());

        // Alert Panel
        cardAlert = v.findViewById(R.id.cardAlert);
        tvAlertMessage = v.findViewById(R.id.tvAlertMessage);

        // Chart Views
        chartBars = new View[]{
                v.findViewById(R.id.viewChartBar1), v.findViewById(R.id.viewChartBar2),
                v.findViewById(R.id.viewChartBar3), v.findViewById(R.id.viewChartBar4),
                v.findViewById(R.id.viewChartBar5), v.findViewById(R.id.viewChartBar6),
                v.findViewById(R.id.viewChartBar7)
        };
        chartValues = new TextView[]{
                v.findViewById(R.id.tvChartVal1), v.findViewById(R.id.tvChartVal2),
                v.findViewById(R.id.tvChartVal3), v.findViewById(R.id.tvChartVal4),
                v.findViewById(R.id.tvChartVal5), v.findViewById(R.id.tvChartVal6),
                v.findViewById(R.id.tvChartVal7)
        };
        chartLabels = new TextView[]{
                v.findViewById(R.id.tvLabel1), v.findViewById(R.id.tvLabel2),
                v.findViewById(R.id.tvLabel3), v.findViewById(R.id.tvLabel4),
                v.findViewById(R.id.tvLabel5), v.findViewById(R.id.tvLabel6),
                v.findViewById(R.id.tvLabel7)
        };

        layoutCashierList = v.findViewById(R.id.layoutCashierList);

        // Export Button (quick action)
        View btnExport = v.findViewById(R.id.btnActionExport);
        if (btnExport != null) {
            btnExport.setOnClickListener(view ->
                    Toast.makeText(getContext(), "Exporting CSV...", Toast.LENGTH_SHORT).show()
            );
        }

        btnRedemptions.setOnClickListener(view -> {
            Fragment newFragment = new RewadLogsFragment();
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, newFragment)
                    .addToBackStack(null)
                    .commit();
        });

        btnActionClients.setOnClickListener(view -> {
                    Fragment newFragment = new ClientsSummaryFragment();
                    requireActivity()
                            .getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, newFragment)
                            .addToBackStack(null)
                            .commit();
                });

        allScansCard.setOnClickListener(view -> {
            Fragment newFragment = new ScanLogsFragment();
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, newFragment)
                    .addToBackStack(null)
                    .commit();
        });

        createCashierCard.setOnClickListener(view -> {
            Fragment newFragment = new CreateCashierFragment();
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, newFragment)
                    .addToBackStack(null)
                    .commit();
        });

    }

    private void setupListeners() {
        if (chipGroupPeriod != null) {
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
    }

    // -------------------------------------------------------------------------
    // PERIOD + LOAD
    // -------------------------------------------------------------------------
    private void applyPeriod(Period period) {
        currentPeriod = period;

        // 1. Calculate Date Range (half-open: [start, end))
        Date[] dateRange = getDateRange(period);
        lastStartTs = new Timestamp(dateRange[0]);
        lastEndTs = new Timestamp(dateRange[1]);

        Log.d(TAG, "applyPeriod " + period +
                " start=" + lastStartTs.toDate() +
                " end=" + lastEndTs.toDate());

        // Reset some UI while loading
        if (tvRevenueValue != null) tvRevenueValue.setText("--");
        if (tvRevenueDelta != null) tvRevenueDelta.setText("--%");
        if (tvRewardCostValue != null) tvRewardCostValue.setText("--");
        if (tvVisitsValue != null) tvVisitsValue.setText("--");
        if (tvNewClientsValue != null) tvNewClientsValue.setText("--");
        if (tvPointsValue != null) tvPointsValue.setText("--");
        if (tvGiftsValue != null) tvGiftsValue.setText("--");
        if (cardAlert != null) cardAlert.setVisibility(View.GONE);

        // 2. Load Data
        loadFinancialsAndVisits(lastStartTs, lastEndTs);
        loadRewardCosts(lastStartTs, lastEndTs);
        loadNewClients(lastStartTs, lastEndTs);

        // 3. Alerts
        checkSuspiciousActivity();

        // 4. Cashier List
        loadCashierPerformance(lastStartTs, lastEndTs);
    }

    // -------------------------------------------------------------------------
    // MAIN DATA LOADING
    // -------------------------------------------------------------------------

    private void loadFinancialsAndVisits(Timestamp start, Timestamp end) {
        db.collection("earn_codes")
                // ⚠️ REMOVE THIS if you don't use "status" or values are not "redeemed"
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, start)
                .whereLessThan(FIELD_EARN_DATE, end)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    Log.d(TAG, "earn_codes (filtered) size = " + snapshots.size());

                    if (snapshots.isEmpty()) {
                        // DEBUG: show a few ALL-TIME docs so you see what dates you really have
                        debugLogSomeEarnCodes();
                    }

                    double totalRevenue = 0;
                    long totalPoints = 0;

                    Set<String> uniqueVisits = new HashSet<>();
                    SimpleDateFormat visitKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

                    int[] chartData = new int[7];
                    Arrays.fill(chartData, 0);

                    for (DocumentSnapshot doc : snapshots) {
                        Double amt = doc.getDouble("amountMAD");
                        if (amt != null) totalRevenue += amt;

                        Long pts = doc.getLong("points");
                        if (pts != null) totalPoints += pts;

                        Timestamp ts = doc.getTimestamp(FIELD_EARN_DATE);
                        String uid = doc.getString("redeemedByUid"); // or "userId" etc.

                        if (ts != null && uid != null) {
                            String key = uid + "_" + visitKeyFormat.format(ts.toDate());
                            uniqueVisits.add(key);
                            processChartData(ts, chartData);
                        }
                    }

                    if (tvRevenueValue != null)
                        tvRevenueValue.setText(String.format(Locale.US, "%.0f", totalRevenue));

                    if (tvPointsValue != null) {
                        if (totalPoints > 1000) {
                            tvPointsValue.setText(String.format(Locale.US, "%.1fk", totalPoints / 1000.0));
                        } else {
                            tvPointsValue.setText(String.valueOf(totalPoints));
                        }
                    }

                    if (tvVisitsValue != null)
                        tvVisitsValue.setText(String.valueOf(uniqueVisits.size()));

                    updateChartUI(chartData);

                    loadRevenueDeltaForPreviousPeriod(start, end, totalRevenue);

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading financials", e);
                    if (!isAdded()) return;
                    Toast.makeText(getContext(),
                            "Error loading data (check Logcat).",
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * DEBUG helper: log a few earn_codes without date filter so you can see actual dates & fields.
     */
    private void debugLogSomeEarnCodes() {
        db.collection("earn_codes")
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    Log.d(TAG, "DEBUG ALL-TIME earn_codes size=" + snap.size());
                    for (DocumentSnapshot doc : snap) {
                        Timestamp tCreated = doc.getTimestamp("createdAt");
                        Timestamp tRedeemed = doc.getTimestamp("redeemedAt");
                        String status = doc.getString("status");
                        Double amt = doc.getDouble("amountMAD");

                        Log.d(TAG, "DOC " + doc.getId() +
                                " status=" + status +
                                " createdAt=" + (tCreated == null ? "null" : tCreated.toDate()) +
                                " redeemedAt=" + (tRedeemed == null ? "null" : tRedeemed.toDate()) +
                                " amountMAD=" + amt);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "debugLogSomeEarnCodes error", e));
    }

    private void loadRewardCosts(Timestamp start, Timestamp end) {
        db.collection("redeem_codes")
                .whereGreaterThanOrEqualTo(FIELD_REDEEM_DATE, start)
                .whereLessThan(FIELD_REDEEM_DATE, end)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    Log.d(TAG, "redeem_codes (filtered) size = " + snapshots.size());

                    double totalCost = 0;
                    int count = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Double cost = doc.getDouble("costPoints");
                        if (cost != null) totalCost += cost;
                        count++;
                    }
                    if (tvRewardCostValue != null)
                        tvRewardCostValue.setText(String.format(Locale.US, "%.0f", totalCost*0.5));
                    if (tvGiftsValue != null)
                        tvGiftsValue.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error loading reward costs", e);
                });
    }

    private void loadNewClients(Timestamp start, Timestamp end) {
        db.collection("users")
                .whereGreaterThanOrEqualTo(FIELD_USER_CREATED, start)
                .whereLessThan(FIELD_USER_CREATED, end)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener((AggregateQuerySnapshot snap) -> {
                    if (!isAdded()) return;
                    Log.d(TAG, "new clients count (filtered) = " + snap.getCount());
                    if (tvNewClientsValue != null)
                        tvNewClientsValue.setText(String.valueOf(snap.getCount()));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error loading new clients", e);
                });
    }

    // -------------------------------------------------------------------------
    // CHART LOGIC
    // -------------------------------------------------------------------------

    private void processChartData(Timestamp ts, int[] data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());
        int index = -1;

        if (currentPeriod == Period.TODAY) {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 8) {
                index = (hour - 8) / 2;
                if (index > 6) index = 6;
            }
        } else if (currentPeriod == Period.WEEK) {
            int day = cal.get(Calendar.DAY_OF_WEEK); // Sun=1
            index = (day == Calendar.SUNDAY) ? 6 : (day - Calendar.MONDAY);
        } else {
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            index = dayOfMonth / 5;
            if (index > 6) index = 6;
        }

        if (index >= 0 && index < 7) {
            data[index]++;
        }
    }

    private void updateChartUI(int[] values) {
        if (!isAdded()) return;
        if (chartBars == null || chartBars[0] == null) return;

        int max = 1;
        for (int v : values) if (v > max) max = v;

        String[] labels;
        if (currentPeriod == Period.TODAY) {
            labels = new String[]{"8", "10", "12", "14", "16", "18", "20+"};
        } else if (currentPeriod == Period.WEEK) {
            labels = new String[]{"M", "T", "W", "T", "F", "S", "S"};
        } else {
            labels = new String[]{"J1-5", "J6-10", "J11-15", "J16-20", "J21-25", "J26-30", "J31+"};
        }

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 7; i++) {
            if (chartValues[i] != null)
                chartValues[i].setText(String.valueOf(values[i]));

            if (chartLabels[i] != null)
                chartLabels[i].setText(labels[i]);

            if (chartBars[i] != null) {
                float percentage = (float) values[i] / max;
                int heightDp = (int) (110 * percentage);
                if (heightDp < 5) heightDp = 5;

                ViewGroup.LayoutParams params = chartBars[i].getLayoutParams();
                params.height = (int) (heightDp * density + 0.5f);
                chartBars[i].setLayoutParams(params);
            }
        }
    }

    // -------------------------------------------------------------------------
    // REVENUE DELTA VS PREVIOUS PERIOD
    // -------------------------------------------------------------------------
    private void loadRevenueDeltaForPreviousPeriod(Timestamp start, Timestamp end, double currentRevenue) {
        if (tvRevenueDelta == null) return;

        Date startDate = start.toDate();
        Date endDate = end.toDate();
        long durationMs = endDate.getTime() - startDate.getTime();

        Date prevEndDate = startDate;
        Date prevStartDate = new Date(prevEndDate.getTime() - durationMs);

        Timestamp prevStart = new Timestamp(prevStartDate);
        Timestamp prevEnd = new Timestamp(prevEndDate);

        db.collection("earn_codes")
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, prevStart)
                .whereLessThan(FIELD_EARN_DATE, prevEnd)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    double previousRevenue = 0.0;
                    for (DocumentSnapshot doc : snap) {
                        Double amt = doc.getDouble("amountMAD");
                        if (amt != null) previousRevenue += amt;
                    }

                    if (previousRevenue <= 0) {
                        tvRevenueDelta.setText("--%");
                    } else {
                        double delta = ((currentRevenue - previousRevenue) / previousRevenue) * 100.0;
                        tvRevenueDelta.setText(String.format(Locale.US, "%.1f%%", delta));
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error loading revenue delta", e);
                    tvRevenueDelta.setText("--%");
                });
    }

    // -------------------------------------------------------------------------
    // ALERTS
    // -------------------------------------------------------------------------
    private void checkSuspiciousActivity() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        Timestamp oneHourAgo = new Timestamp(cal.getTime());

        db.collection("earn_codes")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, oneHourAgo)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    int count = snap.size();
                    if (count > 50) {
                        if (cardAlert != null && tvAlertMessage != null) {
                            cardAlert.setVisibility(View.VISIBLE);
                            tvAlertMessage.setText(
                                    "Warning: High traffic (" + count + " scans) in last hour."
                            );
                        }
                    } else {
                        if (cardAlert != null) cardAlert.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error checking suspicious activity", e);
                });
    }

    // -------------------------------------------------------------------------
    // DATE UTILS
    // -------------------------------------------------------------------------
    private Date[] getDateRange(Period period) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        // Base: today at 00:00
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        if (period == Period.TODAY) {
            end.setTime(start.getTime());
            end.add(Calendar.DAY_OF_YEAR, 1);

        } else if (period == Period.WEEK) {
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            end.setTime(start.getTime());
            end.add(Calendar.WEEK_OF_YEAR, 1);

        } else {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end.setTime(start.getTime());
            end.add(Calendar.MONTH, 1);
        }

        return new Date[]{start.getTime(), end.getTime()};
    }

    // -------------------------------------------------------------------------
    // CASHIER PERFORMANCE
    // -------------------------------------------------------------------------
    private void loadCashierPerformance(Timestamp start, Timestamp end) {
        if (layoutCashierList == null) return;
        if (!isAdded()) return;

        Map<String, CashierStats> statsMap = new HashMap<>();

        // 1) EARN CODES => "Scans" per cashier
        db.collection("earn_codes")
                .whereGreaterThanOrEqualTo(FIELD_EARN_DATE, start)
                .whereLessThan(FIELD_EARN_DATE, end)
                // If you want only successful scans, uncomment this:
                // .whereEqualTo("status", "redeemed")
                .get()
                .addOnSuccessListener(snapEarn -> {

                    Log.d(TAG, "cashier scans size = " + snapEarn.size());

                    for (DocumentSnapshot doc : snapEarn) {
                        String cashierId = doc.getString("cashierId");
                        String cashierName = doc.getString("cashierName");

                        // Fallbacks for older data that might not have cashier fields
                        if (cashierId == null || cashierId.trim().isEmpty()) {
                            cashierId = "unknown";
                        }
                        if (cashierName == null || cashierName.trim().isEmpty()) {
                            // try legacy fields if they exist
                            cashierName = doc.getString("createdByName");
                        }
                        if (cashierName == null || cashierName.trim().isEmpty()) {
                            cashierName = "Unknown";
                        }

                        CashierStats cs = statsMap.get(cashierId);
                        if (cs == null) {
                            cs = new CashierStats(cashierId, cashierName);
                            statsMap.put(cashierId, cs);
                        }
                        cs.scans++;
                    }

                    // 2) REDEEM CODES => "Redeems" per cashier
                    db.collection("redeem_codes")
                            .whereGreaterThanOrEqualTo(FIELD_REDEEM_DATE, start)
                            .whereLessThan(FIELD_REDEEM_DATE, end)
                            .get()
                            .addOnSuccessListener(snapRedeem -> {
                                Log.d(TAG, "cashier redeems size = " + snapRedeem.size());

                                for (DocumentSnapshot doc : snapRedeem) {
                                    String cashierId = doc.getString("cashierId");
                                    String cashierName = doc.getString("cashierName");

                                    // Fallbacks
                                    if (cashierId == null || cashierId.trim().isEmpty()) {
                                        cashierId = "unknown";
                                    }
                                    if (cashierName == null || cashierName.trim().isEmpty()) {
                                        cashierName = doc.getString("processedByName");
                                    }
                                    if (cashierName == null || cashierName.trim().isEmpty()) {
                                        cashierName = "Unknown";
                                    }

                                    CashierStats cs = statsMap.get(cashierId);
                                    if (cs == null) {
                                        cs = new CashierStats(cashierId, cashierName);
                                        statsMap.put(cashierId, cs);
                                    }
                                    cs.redeems++;
                                }

                                if (!isAdded()) return;
                                renderCashierRows(statsMap);

                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                Log.e(TAG, "Error loading cashier redeems", e);
                                // Even if redeem query fails, show scans
                                renderCashierRows(statsMap);
                            });

                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error loading cashier scans", e);
                    renderCashierRows(new HashMap<>());
                });
    }


    private void renderCashierRows(Map<String, CashierStats> statsMap) {
        if (layoutCashierList == null || !isAdded()) return;


        // Convert map to sorted list
        List<CashierStats> sortedCashiers = getSortedCashiers(statsMap);

        // Render cashier rows
        CashierRowBuilder.renderCashierRows(layoutCashierList, sortedCashiers, getContext(), 5);

        // Handle empty state
        handleEmptyState(sortedCashiers.isEmpty());

        // Show view all button if needed
        handleViewAllButton(sortedCashiers.size() > 5);
    }


    private int calculateTotalTransactions(Map<String, CashierStats> statsMap) {
        int total = 0;
        for (CashierStats stats : statsMap.values()) {
            total += stats.scans + stats.redeems;
        }
        return total;
    }

    private List<CashierStats> getSortedCashiers(Map<String, CashierStats> statsMap) {
        List<CashierStats> list = new ArrayList<>(statsMap.values());

        // Sort by total activity DESC
        Collections.sort(list, (c1, c2) -> {
            int total1 = c1.scans + c1.redeems;
            int total2 = c2.scans + c2.redeems;
            return Integer.compare(total2, total1);
        });

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

    private void showAllCashiers() {
        // Implement navigation to full cashier list
        Toast.makeText(getContext(), "Showing full staff list", Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "??";
        }

        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        for (int i = 0; i < Math.min(parts.length, 2); i++) {
            if (!parts[i].isEmpty()) {
                initials.append(parts[i].charAt(0));
            }
        }

        return initials.toString().toUpperCase();
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
    }
}
