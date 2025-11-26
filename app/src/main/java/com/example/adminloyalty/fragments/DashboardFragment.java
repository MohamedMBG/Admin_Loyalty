package com.example.adminloyalty.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    // Period selection
    private enum Period { TODAY, WEEK, MONTH }
    private Period currentPeriod = Period.TODAY;

    // Views (IDs match your XML)
    private MaterialButton btnToday, btnWeek, btnMonth;
    private TextView tvTotalUsers, tvNewSignups;
    private TextView tvRedemptionRate, tvCaption;
    private ProgressBar progressRedemption;

    // NEW: QR scans
    private LineChart chartQr;
    private TextView tvTotalScans;       // R.id.tv_total_scans

    // Optional deltas
    private TextView tvPercentUsers;     // R.id.percent_users
    private TextView tvPercentNewUsers;  // R.id.percent_new_users

    // System health views
    private View dbDot;
    private TextView tvDbStatus;

    // Firestore
    private FirebaseFirestore db;


    private static final String COLL_EARN_CODES = "earn_codes";
    private static final String F_CREATED_AT    = "createdAt";
    private static final String F_STATUS        = "status";


    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);

        db = FirebaseFirestore.getInstance(); // init FIRST

        bindViews(v);
        setupQrChart();
        setupButtons();

        applyPeriod(Period.TODAY);  // initial load
        pingDatabase();             // set DB health tile

        return v;
    }

    private void bindViews(@NonNull View v) {
        // Time buttons
        btnToday = v.findViewById(R.id.btn_today);
        btnWeek  = v.findViewById(R.id.btn_this_week);
        btnMonth = v.findViewById(R.id.btn_this_month);

        // KPIs
        tvTotalUsers = v.findViewById(R.id.tv_active_users);
        tvNewSignups = v.findViewById(R.id.tv_new_signups);

        // Points
        tvRedemptionRate  = v.findViewById(R.id.tv_redemption_rate);
        tvCaption         = v.findViewById(R.id.text1);
        progressRedemption = v.findViewById(R.id.progress_redemption);

        // QR chart + total scans
        chartQr      = v.findViewById(R.id.chart_qr_scans);
        tvTotalScans = v.findViewById(R.id.tv_total_scans);

        // System Health
        dbDot = v.findViewById(R.id.view_db_dot);
        tvDbStatus = v.findViewById(R.id.tv_db_status);

        // Deltas (if you want to display them)
        tvPercentUsers    = v.findViewById(R.id.percent_users);
        tvPercentNewUsers = v.findViewById(R.id.percent_new_users);
    }

    private void setupButtons() {
        View.OnClickListener l = view -> {
            int id = view.getId();
            if (id == R.id.btn_today)      applyPeriod(Period.TODAY);
            else if (id == R.id.btn_this_week)  applyPeriod(Period.WEEK);
            else if (id == R.id.btn_this_month) applyPeriod(Period.MONTH);
        };
        btnToday.setOnClickListener(l);
        btnWeek.setOnClickListener(l);
        btnMonth.setOnClickListener(l);
    }

    private void applyPeriod(Period p) {
        currentPeriod = p;
        stylePeriodButtons();
        loadKPIs();            // total users + signups in period
        loadPointsEconomy();   // reads /stats/global
        loadQrScansChart();    // << NEW: scans by hour/day
    }

    private void stylePeriodButtons() {
        MaterialButton[] arr = new MaterialButton[]{btnToday, btnWeek, btnMonth};
        for (MaterialButton b : arr) {
            boolean selected =
                    (b.getId() == R.id.btn_today && currentPeriod == Period.TODAY) ||
                            (b.getId() == R.id.btn_this_week && currentPeriod == Period.WEEK) ||
                            (b.getId() == R.id.btn_this_month && currentPeriod == Period.MONTH);

            b.setTextColor(getResources().getColor(
                    selected ? android.R.color.black : android.R.color.darker_gray,
                    requireContext().getTheme()
            ));
            // keep your light gray when selected, transparent otherwise
            b.setBackgroundTintList(getResources().getColorStateList(
                    selected ?  R.color.light_grey /* define #F5F5F5 in colors */ : android.R.color.transparent,
                    requireContext().getTheme()
            ));
            b.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    /** holder of [start, end] for the current period. */
    private static class TRange {
        final Timestamp start;
        final Timestamp end;
        TRange(Timestamp s, Timestamp e) { start = s; end = e; }
    }

    private TRange currentRange() {
        Calendar start = Calendar.getInstance();
        Calendar end   = Calendar.getInstance();

        start.set(Calendar.MILLISECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        switch (currentPeriod) {
            case TODAY:
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);

                end.set(Calendar.HOUR_OF_DAY, 23);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                break;

            case WEEK:
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek()); // usually Monday
                Calendar tmp = (Calendar) start.clone();
                tmp.add(Calendar.DAY_OF_YEAR, 7);
                tmp.add(Calendar.SECOND, -1);
                end = tmp;
                break;

            case MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                Calendar tmp2 = (Calendar) start.clone();
                tmp2.add(Calendar.MONTH, 1);
                tmp2.add(Calendar.SECOND, -1);
                end = tmp2;
                break;
        }
        return new TRange(new Timestamp(start.getTime()), new Timestamp(end.getTime()));
    }

    /** Returns [start,end] timestamps for the previous period of the same length. */
    private TRange previousRange(TRange cur) {
        long durMs = cur.end.toDate().getTime() - cur.start.toDate().getTime() + 1000; // inclusive buffer
        long prevEndMs   = cur.start.toDate().getTime() - 1000;
        long prevStartMs = prevEndMs - durMs + 1000;
        return new TRange(new Timestamp(new java.util.Date(prevStartMs)),
                new Timestamp(new java.util.Date(prevEndMs)));
    }

    /** Loads Total Users (all docs in /users) + New Signups within current period. */
    private void loadKPIs() {
        TRange current = currentRange();
        TRange previous = previousRange(current);

        // 1) Total users (all time)
        db.collection("users").count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(totalSnap -> {
                    long totalNow = totalSnap.getCount();
                    tvTotalUsers.setText(String.valueOf(totalNow));

                    db.collection("users")
                            .whereLessThanOrEqualTo("createdAt", previous.end)
                            .count()
                            .get(AggregateSource.SERVER)
                            .addOnSuccessListener(prevSnap -> {
                                long totalPrev = prevSnap.getCount();
                                double pctUsers = pctChange(totalNow, totalPrev);
                                setDelta(tvPercentUsers, pctUsers);
                            })
                            .addOnFailureListener(e -> setDelta(tvPercentUsers, Double.NaN));
                })
                .addOnFailureListener(e -> tvTotalUsers.setText("—"));

        // 2) New signups in current period
        db.collection("users")
                .whereGreaterThanOrEqualTo("createdAt", current.start)
                .whereLessThanOrEqualTo("createdAt", current.end)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(curSnap -> {
                    long newCur = curSnap.getCount();
                    tvNewSignups.setText(String.valueOf(newCur));

                    db.collection("users")
                            .whereGreaterThanOrEqualTo("createdAt", previous.start)
                            .whereLessThanOrEqualTo("createdAt", previous.end)
                            .count()
                            .get(AggregateSource.SERVER)
                            .addOnSuccessListener(prevSnap -> {
                                long newPrev = prevSnap.getCount();
                                double pctNew = pctChange(newCur, newPrev);
                                setDelta(tvPercentNewUsers, pctNew);
                            })
                            .addOnFailureListener(e -> setDelta(tvPercentNewUsers, Double.NaN));
                })
                .addOnFailureListener(e -> tvNewSignups.setText("—"));
    }

    /** Reads /stats/global and fills Redemption progress. */
    private void loadPointsEconomy() {
        db.collection("stats").document("global").get()
                .addOnSuccessListener(this::applyStatsDoc)
                .addOnFailureListener(e -> {
                    progressRedemption.setProgress(0);
                    tvRedemptionRate.setText("0%");
                    if (tvCaption != null) tvCaption.setText("0 of 0 pts redeemed");
                });
    }

    private void applyStatsDoc(DocumentSnapshot doc) {
        long total = safeLong(doc.get("totalPoints"));
        long redeemed = safeLong(doc.get("redeemedPoints"));
        int pct = (total > 0) ? Math.min(100, (int) Math.round((redeemed * 100.0) / total)) : 0;

        progressRedemption.setMax(100);
        progressRedemption.setProgress(pct);
        tvRedemptionRate.setText(pct + "%");

        if (tvCaption != null) {
            tvCaption.setText(formatPts(redeemed) + " of " + formatPts(total) + " pts redeemed");
        }
    }

    private long safeLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            String s = String.valueOf(v);
            return TextUtils.isEmpty(s) ? 0 : Long.parseLong(s);
        } catch (Exception ignore) { return 0; }
    }

    private String formatPts(long n) { return String.format("%,d", n); }

    /* -------------------------------------------
       QR SCANS LINE CHART
       ------------------------------------------- */

    private void setupQrChart() {
        chartQr.setDrawGridBackground(false);
        chartQr.setScaleEnabled(false);
        chartQr.setDoubleTapToZoomEnabled(false);

        XAxis x = chartQr.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);

        chartQr.getAxisLeft().setDrawGridLines(true);
        chartQr.getAxisLeft().setAxisMinimum(0f);

        chartQr.getAxisRight().setEnabled(true);      // ← enable
        chartQr.getAxisRight().setAxisMinimum(0f);
        chartQr.getAxisRight().setAxisMaximum(100f);

        Description d = new Description(); d.setText("");
        chartQr.setDescription(d);
    }


    /** Query earn_codes scanned in current period, bucket and draw. */
    /** Query earn_codes where status == "redeemed" in current period, bucket and draw. */


    //this is a chart UI helper
    private void styleQrChart(LineChart chart) {
        chart.setViewPortOffsets(32f, 24f, 24f, 40f);
        chart.setExtraOffsets(0f, 8f, 0f, 16f);

        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.getDescription().setEnabled(false);

        // Legend small at top-left
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(11f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setXEntrySpace(12f);

        // X axis – only labels, no lines
        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawAxisLine(false);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setTextSize(11f);
        x.setTextColor(Color.parseColor("#6B7280"));
        x.setYOffset(4f);

        // Left Y axis – soft dashed grid
        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setDrawAxisLine(false);
        left.setDrawGridLines(true);
        left.enableGridDashedLine(10f, 10f, 0f);
        left.setGridColor(Color.parseColor("#E5E7EB"));
        left.setTextColor(Color.parseColor("#94A3B8"));
        left.setTextSize(11f);

        // Right Y axis – success %
        YAxis right = chart.getAxisRight();
        right.setEnabled(true);
        right.setAxisMinimum(0f);
        right.setAxisMaximum(100f);
        right.setDrawAxisLine(false);
        right.setDrawGridLines(false);
        right.setTextColor(Color.parseColor("#94A3B8"));
        right.setTextSize(11f);
        right.setXOffset(-4f);
    }

    /** Visualize ALL earn_codes by createdAt: lines for Redeemed/Pending/Canceled + Success% on right axis. */
    private void loadQrScansChart() {
        if (chartQr == null) return;

        // Base style
        styleQrChart(chartQr);

        chartQr.setNoDataText("No chart data available.");
        chartQr.setNoDataTextColor(
                getResources().getColor(android.R.color.holo_orange_dark, requireContext().getTheme())
        );

        final TRange range = currentRange();

        db.collection(COLL_EARN_CODES)
                .whereGreaterThanOrEqualTo(F_CREATED_AT, range.start)
                .whereLessThanOrEqualTo(F_CREATED_AT, range.end)
                .get()
                .addOnSuccessListener(snap -> {
                    Calendar cal = Calendar.getInstance();

                    // ========== Buckets & labels ==========
                    final int bucketCount;
                    final String[] labels;
                    if (currentPeriod == Period.TODAY) {
                        bucketCount = 24;
                        labels = new String[bucketCount];
                        for (int i = 0; i < bucketCount; i++) {
                            labels[i] = (i % 12 == 0 ? "12" : String.valueOf(i % 12)) + (i < 12 ? "AM" : "PM");
                        }
                    } else if (currentPeriod == Period.WEEK) {
                        bucketCount = 7;
                        labels = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
                    } else {
                        cal.setTime(range.start.toDate());
                        cal.set(Calendar.DAY_OF_MONTH, 1);
                        bucketCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                        labels = new String[bucketCount];
                        for (int i = 0; i < bucketCount; i++) labels[i] = String.valueOf(i + 1);
                    }

                    int[] totalBucket    = new int[bucketCount];
                    int[] redeemedBucket = new int[bucketCount];
                    int[] pendingBucket  = new int[bucketCount];
                    int[] canceledBucket = new int[bucketCount];

                    int totalAll = 0;
                    int redeemedAll = 0;

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Timestamp ts = doc.getTimestamp(F_CREATED_AT);
                        if (ts == null) continue;
                        cal.setTime(ts.toDate());

                        int idx;
                        if (currentPeriod == Period.TODAY) {
                            idx = cal.get(Calendar.HOUR_OF_DAY);
                        } else if (currentPeriod == Period.WEEK) {
                            int dow = cal.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
                            idx = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY); // 0..6 Mon..Sun
                        } else {
                            idx = cal.get(Calendar.DAY_OF_MONTH) - 1; // 0-based
                        }
                        if (idx < 0 || idx >= bucketCount) continue;

                        totalBucket[idx]++;
                        totalAll++;

                        String status = doc.getString(F_STATUS);
                        if ("redeemed".equalsIgnoreCase(status)) {
                            redeemedBucket[idx]++;
                            redeemedAll++;
                        } else if ("pending".equalsIgnoreCase(status)) {
                            pendingBucket[idx]++;
                        } else if ("canceled".equalsIgnoreCase(status)
                                || "cancelled".equalsIgnoreCase(status)) {
                            canceledBucket[idx]++;
                        }
                    }

                    // ========== Top counters ==========
                    if (tvTotalScans != null) {
                        tvTotalScans.setText(
                                String.format(Locale.getDefault(), "%,d", totalAll)
                        );
                    }

                    TextView tvSuccessRate = getView() != null
                            ? getView().findViewById(R.id.tv_success_rate)
                            : null;
                    if (tvSuccessRate != null) {
                        int successNow = (totalAll == 0)
                                ? 0
                                : (int) Math.round((redeemedAll * 100.0) / totalAll);
                        tvSuccessRate.setText(successNow + "%");
                        tvSuccessRate.setTextColor(
                                getResources().getColor(android.R.color.holo_green_dark, requireContext().getTheme())
                        );
                    }

                    TextView tvPeak = getView() != null
                            ? getView().findViewById(R.id.tv_peak_hour)
                            : null;
                    if (tvPeak != null) {
                        int maxIdx = -1, maxVal = -1;
                        for (int i = 0; i < bucketCount; i++) {
                            if (totalBucket[i] > maxVal) {
                                maxVal = totalBucket[i];
                                maxIdx = i;
                            }
                        }
                        if (maxIdx >= 0) tvPeak.setText(labels[maxIdx]);
                    }

                    TextView tvAvg = getView() != null
                            ? getView().findViewById(R.id.tv_avg_hour)
                            : null;
                    if (tvAvg != null) {
                        double avg = (bucketCount == 0) ? 0 : (totalAll * 1.0) / bucketCount;
                        tvAvg.setText(String.valueOf((int) Math.round(avg)));
                    }

                    // ========== No data: clear chart ==========
                    if (totalAll == 0) {
                        chartQr.clear();
                        chartQr.invalidate();
                        TextView delta = getView() != null
                                ? getView().findViewById(R.id.tv_scans_delta)
                                : null;
                        if (delta != null) delta.setText("—");
                        return;
                    }

                    // ========== Build entries (simple) ==========
                    List<Entry> eTotal   = new ArrayList<>(bucketCount);
                    List<Entry> eSuccess = new ArrayList<>(bucketCount);

                    for (int i = 0; i < bucketCount; i++) {
                        eTotal.add(new Entry(i, totalBucket[i]));

                        float pct = totalBucket[i] == 0
                                ? 0f
                                : (redeemedBucket[i] * 100f / totalBucket[i]);
                        eSuccess.add(new Entry(i, pct));
                    }

                    // ========== Modern datasets ==========

                    // Total scans – blue area
                    LineDataSet dsTotal = new LineDataSet(eTotal, "Total scans");
                    dsTotal.setAxisDependency(YAxis.AxisDependency.LEFT);
                    dsTotal.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                    dsTotal.setCubicIntensity(0.2f);
                    dsTotal.setLineWidth(2.4f);
                    dsTotal.setDrawCircles(false);
                    dsTotal.setDrawValues(false);

                    int blue = Color.parseColor("#2563EB");
                    dsTotal.setColor(blue);
                    dsTotal.setHighLightColor(Color.parseColor("#1D4ED8"));
                    dsTotal.setDrawHorizontalHighlightIndicator(false);

                    dsTotal.setDrawFilled(true);
                    dsTotal.setFillAlpha(90);
                    dsTotal.setFillColor(Color.parseColor("#BF2563EB")); // blue with alpha

                    // Success % – thin teal line
                    LineDataSet dsSuccess = new LineDataSet(eSuccess, "Success %");
                    dsSuccess.setAxisDependency(YAxis.AxisDependency.RIGHT);
                    dsSuccess.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                    dsSuccess.setCubicIntensity(0.2f);
                    dsSuccess.setLineWidth(2.0f);
                    dsSuccess.setDrawCircles(false);
                    dsSuccess.setDrawValues(false);
                    dsSuccess.setColor(Color.parseColor("#14B8A6"));

                    LineData lineData = new LineData(dsTotal, dsSuccess);
                    chartQr.setData(lineData);

                    // ========== Axes / labels formatting ==========

                    XAxis x = chartQr.getXAxis();
                    x.setLabelCount(Math.min(labels.length, 6), true);
                    x.setValueFormatter(new ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            int i = Math.round(value);
                            return (i < 0 || i >= labels.length) ? "" : labels[i];
                        }
                    });

                    chartQr.getAxisRight().setValueFormatter(new ValueFormatter() {
                        @Override
                        public String getFormattedValue(float value) {
                            return ((int) value) + "%";
                        }
                    });

                    chartQr.animateY(600);
                    chartQr.invalidate();

                    // ========== Delta vs previous period (same logic) ==========
                    TRange prev = previousRange(range);
                    int finalTotalAll = totalAll;
                    db.collection(COLL_EARN_CODES)
                            .whereGreaterThanOrEqualTo(F_CREATED_AT, prev.start)
                            .whereLessThanOrEqualTo(F_CREATED_AT, prev.end)
                            .count()
                            .get(AggregateSource.SERVER)
                            .addOnSuccessListener(prevSnap -> {
                                long prevCnt = prevSnap.getCount();
                                double pct = (prevCnt == 0)
                                        ? (finalTotalAll == 0 ? 0.0 : 100.0)
                                        : ((finalTotalAll - prevCnt) * 100.0) / prevCnt;

                                TextView delta = getView() != null
                                        ? getView().findViewById(R.id.tv_scans_delta)
                                        : null;
                                if (delta != null) {
                                    String arrow = pct > 0.0001
                                            ? "↑"
                                            : (pct < -0.0001 ? "↓" : "•");
                                    double abs = Math.abs(pct);
                                    delta.setText(String.format(
                                            Locale.getDefault(),
                                            "%s %.1f%%", arrow, abs
                                    ));

                                    int color = pct > 0.0001
                                            ? android.R.color.holo_green_dark
                                            : (pct < -0.0001 ? android.R.color.holo_red_dark
                                            : android.R.color.darker_gray);
                                    delta.setTextColor(
                                            getResources().getColor(color, requireContext().getTheme())
                                    );
                                }
                            })
                            .addOnFailureListener(e -> {
                                TextView delta = getView() != null
                                        ? getView().findViewById(R.id.tv_scans_delta)
                                        : null;
                                if (delta != null) delta.setText("—");
                            });

                })
                .addOnFailureListener(e -> {
                    if (tvTotalScans != null) tvTotalScans.setText("—");
                    chartQr.clear();
                    chartQr.invalidate();
                });
    }


    /* -------------------------------------------
       DB HEALTH + helpers
       ------------------------------------------- */

    /** Read-only health check: a tiny GET to /users (limit 1). */
    private void pingDatabase() {
        db.collection("users").limit(1).get()
                .addOnSuccessListener(snap -> setDbHealth(true))
                .addOnFailureListener(e -> setDbHealth(false));
    }

    private void setDbHealth(boolean ok) {
        if (dbDot == null || tvDbStatus == null) return;
        dbDot.setBackgroundResource(ok ? R.drawable.circle_green : R.drawable.circle_red);
        tvDbStatus.setText(ok ? "Online" : "Error");
        tvDbStatus.setTextColor(getResources().getColor(
                ok ? android.R.color.holo_green_dark : android.R.color.holo_red_dark,
                requireContext().getTheme()
        ));
    }

    /** Pretty “↑ 5.2% / ↓ 1.1% / —” with colors. */
    private void setDelta(TextView tv, double pct) {
        if (tv == null) return;
        if (Double.isNaN(pct)) {
            tv.setText("—");
            tv.setTextColor(getResources().getColor(android.R.color.darker_gray, requireContext().getTheme()));
            return;
        }
        String arrow = pct > 0.0001 ? "↑" : (pct < -0.0001 ? "↓" : "•");
        double abs = Math.abs(pct);
        tv.setText(String.format(Locale.getDefault(), "%s %.1f%%", arrow, abs));
        int color = pct > 0.0001
                ? getResources().getColor(android.R.color.holo_green_dark, requireContext().getTheme())
                : (pct < -0.0001
                ? getResources().getColor(android.R.color.holo_red_dark, requireContext().getTheme())
                : getResources().getColor(android.R.color.darker_gray, requireContext().getTheme()));
        tv.setTextColor(color);
    }

    /** Safe percent change: (now - prev) / max(prev,1) * 100. If both zero, returns 0. */
    private double pctChange(long now, long prev) {
        if (prev == 0L) return (now == 0L) ? 0.0 : 100.0;
        return ((now - prev) * 100.0) / prev;
    }
}
