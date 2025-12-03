package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.R;
import com.example.adminloyalty.cashier.RedeemingActivity;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
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
    private CardView cardAlert , allScansCard, btnRedemptions;
    private TextView tvAlertMessage;

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

        btnRedemptions.setOnClickListener(view ->{
                // we will open the RedemptionLogsFragment here
                Fragment newFragment  = new RewadLogsFragment();
                requireActivity()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, newFragment)
                        .addToBackStack(null)
                        .commit();
        });

        allScansCard.setOnClickListener(view ->{
                // we will open the ScanLogsFragment here
                Fragment newFragment  = new ScanLogsFragment();
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

        // 1. Calculate Date Range
        Date[] dateRange = getDateRange(period);
        lastStartTs = new Timestamp(dateRange[0]);
        lastEndTs = new Timestamp(dateRange[1]);

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

        // 3. Alerts (independent of selected period)
        checkSuspiciousActivity();

        // 4. Cashier List (independent of selected period)
        loadCashierPerformance(lastStartTs, lastEndTs);

    }

    // -------------------------------------------------------------------------
    // MAIN DATA LOADING
    // -------------------------------------------------------------------------

    /**
     * Loads Revenue, Points, Visits and populates the Traffic Chart
     * from "earn_codes".
     *
     * Requires a composite index on:
     *   status + redeemedAt
     */
    private void loadFinancialsAndVisits(Timestamp start, Timestamp end) {
        db.collection("earn_codes")
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo("redeemedAt", start)
                .whereLessThanOrEqualTo("redeemedAt", end)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    double totalRevenue = 0;
                    long totalPoints = 0;

                    // For Visits Logic (unique client per day)
                    Set<String> uniqueVisits = new HashSet<>();
                    SimpleDateFormat visitKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

                    // For Chart Logic
                    int[] chartData = new int[7];
                    Arrays.fill(chartData, 0);

                    for (DocumentSnapshot doc : snapshots) {
                        // 1. Revenue
                        Double amt = doc.getDouble("amountMAD");
                        if (amt != null) totalRevenue += amt;

                        // 2. Points
                        Long pts = doc.getLong("points");
                        if (pts != null) totalPoints += pts;

                        // 3. Unique Visits + Chart data
                        Timestamp ts = doc.getTimestamp("redeemedAt");
                        String uid = doc.getString("redeemedByUid");

                        if (ts != null && uid != null) {
                            String key = uid + "_" + visitKeyFormat.format(ts.toDate());
                            uniqueVisits.add(key);

                            // 4. Populate Chart Buckets
                            processChartData(ts, chartData);
                        }
                    }

                    // Update UI
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

                    // Update Chart Visuals
                    updateChartUI(chartData);

                    // Revenue delta vs previous period
                    loadRevenueDeltaForPreviousPeriod(start, end, totalRevenue);

                })
                .addOnFailureListener(e -> {
                    Log.e("Dashboard", "Error loading financials", e);
                    if (!isAdded()) return;
                    Toast.makeText(getContext(),
                            "Error loading data (maybe index missing). Check Logcat.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void loadRewardCosts(Timestamp start, Timestamp end) {
        // Assumes 'redeem_codes' for burnt points / gifts
        db.collection("redeem_codes")
                .whereGreaterThanOrEqualTo("createdAt", start)
                .whereLessThanOrEqualTo("createdAt", end)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;

                    double totalCost = 0;
                    int count = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Double cost = doc.getDouble("costMAD"); // adapt to your schema if needed
                        if (cost != null) totalCost += cost;
                        count++;
                    }
                    if (tvRewardCostValue != null)
                        tvRewardCostValue.setText(String.format(Locale.US, "%.0f", totalCost));
                    if (tvGiftsValue != null)
                        tvGiftsValue.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e("Dashboard", "Error loading reward costs", e);
                });
    }

    private void loadNewClients(Timestamp start, Timestamp end) {
        db.collection("users")
                .whereGreaterThanOrEqualTo("createdAt", start)
                .whereLessThanOrEqualTo("createdAt", end)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener((AggregateQuerySnapshot snap) -> {
                    if (!isAdded()) return;
                    if (tvNewClientsValue != null)
                        tvNewClientsValue.setText(String.valueOf(snap.getCount()));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e("Dashboard", "Error loading new clients", e);
                });
    }

    // -------------------------------------------------------------------------
    // CHART LOGIC
    // -------------------------------------------------------------------------

    /**
     * Buckets a timestamp into the correct index [0-6] for the chart array.
     */
    private void processChartData(Timestamp ts, int[] data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());
        int index = -1;

        if (currentPeriod == Period.TODAY) {
            // Bucket by 2-hour slots starting from 8am
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 8) {
                index = (hour - 8) / 2;
                if (index > 6) index = 6;
            }
        } else if (currentPeriod == Period.WEEK) {
            // Bucket by Day of Week (Mon=0 ... Sun=6)
            int day = cal.get(Calendar.DAY_OF_WEEK); // Sun=1
            index = (day == Calendar.SUNDAY) ? 6 : (day - Calendar.MONDAY);
        } else {
            // Month: Bucket by 5-day chunks of month
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            index = dayOfMonth / 5; // 1-5 -> 0, 6-10 -> 1, ...
            if (index > 6) index = 6;
        }

        if (index >= 0 && index < 7) {
            data[index]++;
        }
    }

    private void updateChartUI(int[] values) {
        if (!isAdded()) return;
        if (chartBars == null || chartBars[0] == null) return;

        // 1. Find Max for scaling
        int max = 1;
        for (int v : values) if (v > max) max = v;

        // 2. Define Labels based on Period
        String[] labels;
        if (currentPeriod == Period.TODAY) {
            labels = new String[]{"8", "10", "12", "14", "16", "18", "20+"};
        } else if (currentPeriod == Period.WEEK) {
            labels = new String[]{"M", "T", "W", "T", "F", "S", "S"};
        } else {
            labels = new String[]{"J1-5", "J6-10", "J11-15", "J16-20", "J21-25", "J26-30", "J31+"};
        }

        // 3. Render
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 7; i++) {
            // Set Text Value
            if (chartValues[i] != null)
                chartValues[i].setText(String.valueOf(values[i]));

            // Set Label
            if (chartLabels[i] != null)
                chartLabels[i].setText(labels[i]);

            // Set Height
            if (chartBars[i] != null) {
                float percentage = (float) values[i] / max;
                int heightDp = (int) (110 * percentage); // Max height 110dp
                if (heightDp < 5) heightDp = 5; // Min visibility

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

        // Previous period: same length just before current start
        Date prevEndDate = startDate;
        Date prevStartDate = new Date(prevEndDate.getTime() - durationMs);

        Timestamp prevStart = new Timestamp(prevStartDate);
        Timestamp prevEnd = new Timestamp(prevEndDate);

        db.collection("earn_codes")
                .whereEqualTo("status", "redeemed")
                .whereGreaterThanOrEqualTo("redeemedAt", prevStart)
                .whereLessThanOrEqualTo("redeemedAt", prevEnd)
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
                    Log.e("Dashboard", "Error loading revenue delta", e);
                    tvRevenueDelta.setText("--%");
                });
    }

    // -------------------------------------------------------------------------
    // ALERTS
    // -------------------------------------------------------------------------
    private void checkSuspiciousActivity() {
        // Example rule: > 50 scans in the last hour
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        Timestamp oneHourAgo = new Timestamp(cal.getTime());

        db.collection("earn_codes")
                .whereGreaterThanOrEqualTo("createdAt", oneHourAgo) // adapt field if needed
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    int count = snap.size();
                    if (count > 50) { // Threshold
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
                    Log.e("Dashboard", "Error checking suspicious activity", e);
                });
    }

    // -------------------------------------------------------------------------
    // DATE UTILS
    // -------------------------------------------------------------------------
    private Date[] getDateRange(Period period) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        // End is end of today
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        // Base start = today 00:00
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        if (period == Period.WEEK) {
            // Monday of current week
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        } else if (period == Period.MONTH) {
            // 1st day of current month
            start.set(Calendar.DAY_OF_MONTH, 1);
        }

        return new Date[]{start.getTime(), end.getTime()};
    }

    private void loadCashierPerformance(Timestamp start, Timestamp end) {
        if (layoutCashierList == null) return;
        if (!isAdded()) return;

        // Map<cashierName, CashierStats>
        Map<String, CashierStats> statsMap = new HashMap<>();

        // 1) D'abord les SCANS (earn_codes)
        db.collection("earn_codes")
                .whereGreaterThanOrEqualTo("createdAt", start)
                .whereLessThanOrEqualTo("createdAt", end)
                .get()
                .addOnSuccessListener(snapEarn -> {

                    for (DocumentSnapshot doc : snapEarn) {
                        // 🔁 Adapte ces champs à ton schéma
                        String cashierName = doc.getString("cashierName");
                        if (cashierName == null) {
                            cashierName = doc.getString("createdByName");
                        }
                        if (cashierName == null || cashierName.trim().isEmpty()) {
                            cashierName = "Unknown";
                        }

                        CashierStats cs = statsMap.get(cashierName);
                        if (cs == null) {
                            cs = new CashierStats(cashierName);
                            statsMap.put(cashierName, cs);
                        }
                        cs.scans++;
                    }

                    // 2) Ensuite les REDEEMS (redeem_codes)
                    db.collection("redeem_codes")
                            .whereGreaterThanOrEqualTo("createdAt", start)
                            .whereLessThanOrEqualTo("createdAt", end)
                            .get()
                            .addOnSuccessListener(snapRedeem -> {
                                for (DocumentSnapshot doc : snapRedeem) {
                                    // 🔁 Adapte ces champs à ton schéma
                                    String cashierName = doc.getString("cashierName");
                                    if (cashierName == null) {
                                        cashierName = doc.getString("processedByName");
                                    }
                                    if (cashierName == null || cashierName.trim().isEmpty()) {
                                        cashierName = "Unknown";
                                    }

                                    CashierStats cs = statsMap.get(cashierName);
                                    if (cs == null) {
                                        cs = new CashierStats(cashierName);
                                        statsMap.put(cashierName, cs);
                                    }
                                    cs.redeems++;
                                }

                                // 3) On met à jour l’UI
                                if (!isAdded()) return;
                                renderCashierRows(statsMap);

                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                Log.e("Dashboard", "Error loading cashier redeems", e);
                                renderCashierRows(statsMap); // quand même afficher les scans
                            });

                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e("Dashboard", "Error loading cashier scans", e);
                    // On vide la liste pour montrer que rien n'a été chargé
                    renderCashierRows(new HashMap<>());
                });
    }

    private void renderCashierRows(Map<String, CashierStats> statsMap) {
        if (layoutCashierList == null) return;
        if (!isAdded()) return;

        // Garder la première ligne (header) et supprimer les anciennes lignes dynamiques
        int childCount = layoutCashierList.getChildCount();
        if (childCount > 1) {
            layoutCashierList.removeViews(1, childCount - 1);
        }

        if (statsMap.isEmpty()) {
            // Optionnel : afficher une petite ligne "No data"
            TextView tv = new TextView(getContext());
            tv.setText("No data for this period");
            tv.setTextSize(12);
            tv.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tv.setPadding(24, 16, 24, 16);
            layoutCashierList.addView(tv);
            return;
        }

        // Convertir la map en liste pour trier
        List<CashierStats> list = new ArrayList<>(statsMap.values());

        // Trier par total (scans + redeems) décroissant
        Collections.sort(list, new Comparator<CashierStats>() {
            @Override
            public int compare(CashierStats o1, CashierStats o2) {
                int total1 = o1.scans + o1.redeems;
                int total2 = o2.scans + o2.redeems;
                return Integer.compare(total2, total1);
            }
        });

        int paddingHorizontal = (int) (16 * getResources().getDisplayMetrics().density);
        int paddingVertical = (int) (10 * getResources().getDisplayMetrics().density);

        for (CashierStats cs : list) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            row.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

            // Colonne 1 : Nom du caissier (weight 2)
            TextView tvName = new TextView(getContext());
            LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
            tvName.setLayoutParams(lpName);
            tvName.setText(cs.name);
            tvName.setTextSize(13);
            tvName.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            row.addView(tvName);

            // Colonne 2 : Scans (weight 1, centré)
            TextView tvScans = new TextView(getContext());
            LinearLayout.LayoutParams lpScans = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvScans.setLayoutParams(lpScans);
            tvScans.setText(String.valueOf(cs.scans));
            tvScans.setTextSize(13);
            tvScans.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tvScans.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            row.addView(tvScans);

            // Colonne 3 : Redeems (weight 1, aligné à droite)
            TextView tvRedeems = new TextView(getContext());
            LinearLayout.LayoutParams lpRedeems = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvRedeems.setLayoutParams(lpRedeems);
            tvRedeems.setText(String.valueOf(cs.redeems));
            tvRedeems.setTextSize(13);
            tvRedeems.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            tvRedeems.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            row.addView(tvRedeems);

            // Ligne séparatrice légère (optionnel)
            View divider = new View(getContext());
            LinearLayout.LayoutParams lpDivider = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (0.5f * getResources().getDisplayMetrics().density)
            );
            divider.setLayoutParams(lpDivider);
            divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

            layoutCashierList.addView(row);
            layoutCashierList.addView(divider);
        }
    }



    private static class CashierStats {
        String name;
        int scans;
        int redeems;

        CashierStats(String name) {
            this.name = name;
        }
    }

}
