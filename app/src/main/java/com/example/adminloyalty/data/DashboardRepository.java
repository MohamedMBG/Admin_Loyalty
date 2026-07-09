package com.example.adminloyalty.data;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dashboard data via the backend analytics endpoint. Keeps the in-memory + disk cache so tab toggles
 * don't re-fetch. Replaces the old direct earn_codes/redeem_codes/users aggregate queries (rules-denied)
 * with two {@code GET /admin/analytics} calls — one for the current period, one for the previous
 * (for the revenue delta).
 */
@Singleton
public class DashboardRepository {

    private final AdminApiClient api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, DashboardData> cache = new HashMap<>();
    private final SharedPreferences prefs;

    @Inject
    public DashboardRepository(@NonNull AdminApiClient api, @ApplicationContext Context context) {
        this.api = api;
        this.prefs = context.getSharedPreferences("dashboard_cache", Context.MODE_PRIVATE);
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void loadDashboard(@NonNull DashboardPeriod period, @NonNull DashboardCallback callback) {
        DateRange range = DateRange.forPeriod(period);
        String cacheKey = period.name() + range.start.getTime();

        DashboardData memCached = cache.get(cacheKey);
        if (memCached != null) {
            callback.onSuccess(memCached, true);
        } else {
            DashboardData diskCached = loadFromPreferences(cacheKey);
            if (diskCached != null) {
                cache.put(cacheKey, diskCached);
                callback.onSuccess(diskCached, true);
            }
        }

        DateRange prevRange = DateRange.previousOf(range.start, range.end);
        executor.execute(() -> {
            ApiResult current = api.get(analyticsPath(range));
            if (!current.isOk() || current.data == null) {
                callback.onError("Failed to load dashboard data");
                return;
            }
            // Previous period is only for the revenue delta — a failure there degrades to 0, not an error.
            ApiResult prev = api.get(analyticsPath(prevRange));
            double prevRevenue = (prev.isOk() && prev.data != null) ? prev.data.optDouble("revenue", 0.0) : 0.0;

            DashboardData data = parseAnalytics(period, range, current.data, prevRevenue);
            cache.put(cacheKey, data);
            saveToPreferences(cacheKey, data);
            callback.onSuccess(data, false);
        });
    }

    private static String analyticsPath(@NonNull DateRange range) {
        return "/admin/analytics?from=" + range.start.getTime() + "&to=" + range.end.getTime();
    }

    @VisibleForTesting
    static DashboardData parseAnalytics(@NonNull DashboardPeriod period, @NonNull DateRange range,
                                        @NonNull JSONObject d, double prevRevenue) {
        double revenue = d.optDouble("revenue", 0.0);
        long points = d.optLong("pointsIssued", 0);
        double pointsRedeemed = d.optLong("pointsRedeemed", 0);
        int gifts = (int) d.optLong("gifts", 0);
        long newClients = d.optLong("newClients", 0);
        int uniqueVisits = (int) d.optLong("uniqueVisitors", 0);

        int size = getChartSize(period);
        int[] chartData = new int[size];
        JSONArray series = d.optJSONArray("series");
        if (series != null) {
            // ponytail: per-day series mapped straight into the chart slots (backend has no hourly
            // bucketing). Faithful for WEEK; TODAY/MONTH are approximate — accepted degrade.
            for (int i = 0; i < series.length() && i < size; i++) {
                JSONObject bucket = series.optJSONObject(i);
                if (bucket != null) chartData[i] = (int) bucket.optLong("earnCount", 0);
            }
        }

        List<CashierStats> cashiers = new ArrayList<>();
        JSONArray cs = d.optJSONArray("cashiers");
        if (cs != null) {
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.optJSONObject(i);
                if (c == null) continue;
                CashierStats stat = new CashierStats(c.optString("cashierUid", ""), c.optString("cashierName", ""));
                stat.scans = (int) c.optLong("codesIssued", 0);
                stat.redeems = (int) c.optLong("redeemsCompleted", 0);
                cashiers.add(stat);
            }
        }

        return new DashboardData(period, range, revenue, prevRevenue, points, uniqueVisits, chartData,
                pointsRedeemed, gifts, newClients, cashiers);
    }

    private static int getChartSize(@NonNull DashboardPeriod period) {
        // All periods render 7 chart slots in the old UI.
        return 7;
    }

    private void saveToPreferences(String cacheKey, DashboardData data) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("period", data.period.name());
            obj.put("rangeStart", data.range.start.getTime());
            obj.put("rangeEnd", data.range.end.getTime());
            obj.put("revenue", data.revenue);
            obj.put("previousRevenue", data.previousRevenue);
            obj.put("points", data.points);
            obj.put("uniqueVisits", data.uniqueVisits);

            JSONArray chartArr = new JSONArray();
            for (int v : data.chartData) chartArr.put(v);
            obj.put("chartData", chartArr);

            obj.put("totalCostPoints", data.totalCostPoints);
            obj.put("gifts", data.gifts);
            obj.put("newClients", data.newClients);

            JSONArray cashiersArr = new JSONArray();
            for (CashierStats cs : data.cashiers) {
                JSONObject c = new JSONObject();
                c.put("id", cs.id);
                c.put("name", cs.name);
                c.put("scans", cs.scans);
                c.put("redeems", cs.redeems);
                cashiersArr.put(c);
            }
            obj.put("cashiers", cashiersArr);

            prefs.edit().putString(cacheKey, obj.toString()).apply();
        } catch (Exception ignored) { }
    }

    private DashboardData loadFromPreferences(String cacheKey) {
        String json = prefs.getString(cacheKey, null);
        if (json == null) return null;
        try {
            JSONObject obj = new JSONObject(json);
            DashboardPeriod period = DashboardPeriod.valueOf(obj.getString("period"));
            DateRange range = new DateRange(new Date(obj.getLong("rangeStart")), new Date(obj.getLong("rangeEnd")));

            JSONArray chartArr = obj.getJSONArray("chartData");
            int[] chartData = new int[chartArr.length()];
            for (int i = 0; i < chartArr.length(); i++) chartData[i] = chartArr.getInt(i);

            JSONArray cashiersArr = obj.getJSONArray("cashiers");
            List<CashierStats> cashiers = new ArrayList<>();
            for (int i = 0; i < cashiersArr.length(); i++) {
                JSONObject c = cashiersArr.getJSONObject(i);
                CashierStats cs = new CashierStats(c.getString("id"), c.getString("name"));
                cs.scans = c.getInt("scans");
                cs.redeems = c.getInt("redeems");
                cashiers.add(cs);
            }

            return new DashboardData(
                    period, range,
                    obj.getDouble("revenue"), obj.getDouble("previousRevenue"),
                    obj.getLong("points"), obj.getInt("uniqueVisits"),
                    chartData, obj.getDouble("totalCostPoints"),
                    obj.getInt("gifts"), obj.getLong("newClients"),
                    cashiers
            );
        } catch (Exception e) {
            return null;
        }
    }

    public interface DashboardCallback {
        void onSuccess(@NonNull DashboardData data, boolean fromCache);

        void onError(@NonNull String message);
    }

    public enum DashboardPeriod { TODAY, WEEK, MONTH }

    public static final class DashboardData {
        public final DashboardPeriod period;
        public final DateRange range;
        public final double revenue;
        public final double previousRevenue;
        public final long points;
        public final int uniqueVisits;
        public final int[] chartData;
        public final double totalCostPoints;
        public final int gifts;
        public final long newClients;
        public final List<CashierStats> cashiers;

        public DashboardData(DashboardPeriod period,
                              DateRange range,
                              double revenue,
                              double previousRevenue,
                              long points,
                              int uniqueVisits,
                              int[] chartData,
                              double totalCostPoints,
                              int gifts,
                              long newClients,
                              List<CashierStats> cashiers) {
            this.period = period;
            this.range = range;
            this.revenue = revenue;
            this.previousRevenue = previousRevenue;
            this.points = points;
            this.uniqueVisits = uniqueVisits;
            this.chartData = chartData;
            this.totalCostPoints = totalCostPoints;
            this.gifts = gifts;
            this.newClients = newClients;
            this.cashiers = cashiers;
        }
    }

    public static final class CashierStats {
        public final String id;
        public final String name;
        public int scans;
        public int redeems;

        public CashierStats(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getTotalActivity() {
            return scans + redeems;
        }
    }

    public static final class DateRange {
        public final Date start;
        public final Date end;

        public DateRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }

        public static DateRange forPeriod(@NonNull DashboardPeriod p) {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();

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

        public static DateRange previousOf(@NonNull Date currentStart, @NonNull Date currentEnd) {
            long duration = currentEnd.getTime() - currentStart.getTime();
            Date prevEnd = currentStart;
            Date prevStart = new Date(prevEnd.getTime() - duration);
            return new DateRange(prevStart, prevEnd);
        }
    }
}
