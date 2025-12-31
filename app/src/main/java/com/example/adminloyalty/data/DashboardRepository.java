package com.example.adminloyalty.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateQuerySnapshot;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Repository holding all Firestore calls for dashboard data.
 * Uses simple in-memory cache (per period) to avoid repeated reads when toggling tabs.
 */
public class DashboardRepository {

    private static final String COL_EARN = "earn_codes";
    private static final String COL_REDEEM = "redeem_codes";
    private static final String COL_USERS = "users";

    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_REDEEMED = "redeemed";
    private static final String FIELD_AMOUNT_MAD = "amountMAD";
    private static final String FIELD_POINTS = "points";
    private static final String FIELD_COST_POINTS = "costPoints";
    private static final String FIELD_REDEEMED_BY_UID = "redeemedByUid";
    private static final String FIELD_CASHIER_NAME = "cashierName";
    private static final String FIELD_CREATED_BY_NAME = "createdByName";
    private static final String FIELD_PROCESSED_BY_NAME = "processedByName";

    private final FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, DashboardData> cache = new HashMap<>();

    public DashboardRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public DashboardRepository(@NonNull FirebaseFirestore db) {
        this.db = db;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void loadDashboard(@NonNull DashboardPeriod period, @NonNull DashboardCallback callback) {
        DateRange range = DateRange.forPeriod(period);
        String cacheKey = period.name() + range.start.getTime();

        DashboardData cached = cache.get(cacheKey);
        if (cached != null) {
            callback.onSuccess(cached, true);
            return;
        }

        Task<QuerySnapshot> earnTask = db.collection(COL_EARN)
                .whereEqualTo(FIELD_STATUS, STATUS_REDEEMED)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, new Timestamp(range.start))
                .whereLessThan(FIELD_CREATED_AT, new Timestamp(range.end))
                .get();

        DateRange prevRange = DateRange.previousOf(range.start, range.end);
        Task<QuerySnapshot> prevRevenueTask = db.collection(COL_EARN)
                .whereEqualTo(FIELD_STATUS, STATUS_REDEEMED)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, new Timestamp(prevRange.start))
                .whereLessThan(FIELD_CREATED_AT, new Timestamp(prevRange.end))
                .get();

        Task<QuerySnapshot> redeemTask = db.collection(COL_REDEEM)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, new Timestamp(range.start))
                .whereLessThan(FIELD_CREATED_AT, new Timestamp(range.end))
                .get();

        Task<AggregateQuerySnapshot> newClientsTask = db.collection(COL_USERS)
                .whereGreaterThanOrEqualTo(FIELD_CREATED_AT, new Timestamp(range.start))
                .whereLessThan(FIELD_CREATED_AT, new Timestamp(range.end))
                .count()
                .get(AggregateSource.SERVER);

        List<Task<?>> tasks = Arrays.asList(earnTask, prevRevenueTask, redeemTask, newClientsTask);
        AtomicBoolean delivered = new AtomicBoolean(false);

        Tasks.whenAllComplete(tasks).addOnCompleteListener(all -> {
            if (delivered.get()) return;

            boolean anyFailed = false;
            for (Task<?> t : tasks) {
                if (!t.isSuccessful()) {
                    anyFailed = true;
                    break;
                }
            }

            if (anyFailed) {
                delivered.set(true);
                callback.onError("Failed to load dashboard data");
                return;
            }

            executor.execute(() -> {
                DashboardData data = buildDashboardData(period, range, earnTask.getResult(), prevRevenueTask.getResult(), redeemTask.getResult(), newClientsTask.getResult());
                cache.put(cacheKey, data);
                delivered.set(true);
                callback.onSuccess(data, false);
            });
        });
    }

    @androidx.annotation.VisibleForTesting
    DashboardData buildDashboardData(@NonNull DashboardPeriod period,
                                     @NonNull DateRange range,
                                     @NonNull QuerySnapshot earnSnap,
                                     @NonNull QuerySnapshot prevRevenueSnap,
                                     @NonNull QuerySnapshot redeemSnap,
                                     @NonNull AggregateQuerySnapshot newClientsSnap) {
        double revenue = 0.0;
        long points = 0L;
        Set<String> uniqueVisits = new HashSet<>();
        int[] chartData = new int[getChartSize(period)];

        SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        for (DocumentSnapshot doc : earnSnap.getDocuments()) {
            revenue += safeDouble(doc, FIELD_AMOUNT_MAD);
            points += safeLong(doc, FIELD_POINTS);

            Timestamp ts = doc.getTimestamp(FIELD_CREATED_AT);
            String uid = doc.getString(FIELD_REDEEMED_BY_UID);
            if (ts != null && uid != null && !uid.trim().isEmpty()) {
                uniqueVisits.add(uid + "_" + dayKeyFormat.format(ts.toDate()));
                incrementChartBucket(period, ts, chartData);
            }
        }

        double prevRevenue = 0.0;
        for (DocumentSnapshot doc : prevRevenueSnap.getDocuments()) {
            prevRevenue += safeDouble(doc, FIELD_AMOUNT_MAD);
        }

        double totalCostPoints = 0.0;
        List<CashierStats> cashiers = new ArrayList<>();
        Map<String, CashierStats> cashierMap = new HashMap<>();
        for (DocumentSnapshot doc : redeemSnap.getDocuments()) {
            totalCostPoints += safeDouble(doc, FIELD_COST_POINTS);
            CashierInfo info = extractCashierInfo(doc, FIELD_CASHIER_NAME, FIELD_PROCESSED_BY_NAME);
            statsFor(cashierMap, info).redeems++;
        }

        for (DocumentSnapshot doc : earnSnap.getDocuments()) {
            CashierInfo info = extractCashierInfo(doc, FIELD_CASHIER_NAME, FIELD_CREATED_BY_NAME);
            statsFor(cashierMap, info).scans++;
        }

        cashiers.addAll(cashierMap.values());
        Collections.sort(cashiers, (a, b) -> Integer.compare(b.getTotalActivity(), a.getTotalActivity()));

        int gifts = redeemSnap.size();
        long newClients = newClientsSnap.getCount();

        return new DashboardData(period, range, revenue, prevRevenue, points, uniqueVisits.size(), chartData, totalCostPoints, gifts, newClients, cashiers);
    }

    private void incrementChartBucket(@NonNull DashboardPeriod period, @NonNull Timestamp ts, @NonNull int[] data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ts.toDate());
        int index = -1;
        switch (period) {
            case TODAY:
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour >= 8) index = Math.min((hour - 8) / 2, data.length - 1);
                break;
            case WEEK:
                index = weekdayBucket(cal.get(Calendar.DAY_OF_WEEK));
                break;
            case MONTH:
                index = monthBucket(cal.get(Calendar.DAY_OF_MONTH), data.length);
                break;
        }
        if (index >= 0 && index < data.length) data[index]++;
    }

    private int weekdayBucket(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return 0;
            case Calendar.TUESDAY:
                return 1;
            case Calendar.WEDNESDAY:
                return 2;
            case Calendar.THURSDAY:
                return 3;
            case Calendar.FRIDAY:
                return 4;
            case Calendar.SATURDAY:
                return 5;
            case Calendar.SUNDAY:
                return 6;
            default:
                return -1;
        }
    }

    private int monthBucket(int dayOfMonth, int size) {
        if (dayOfMonth <= 0) return -1;
        int index = (int) ((dayOfMonth - 1) / Math.ceil(31f / size));
        return Math.min(index, size - 1);
    }

    private int getChartSize(@NonNull DashboardPeriod period) {
        switch (period) {
            case TODAY:
            case WEEK:
                return 7;
            case MONTH:
                return 7;
            default:
                return 7;
        }
    }

    private double safeDouble(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Double d = doc.getDouble(field);
        return d != null ? d : 0.0;
    }

    private long safeLong(@NonNull DocumentSnapshot doc, @NonNull String field) {
        Long l = doc.getLong(field);
        return l != null ? l : 0L;
    }

    private CashierInfo extractCashierInfo(@NonNull DocumentSnapshot doc, @NonNull String primaryField, @NonNull String fallbackField) {
        String id = doc.getString("cashierId");
        String name = doc.getString(primaryField);
        if (name == null || name.trim().isEmpty()) {
            name = doc.getString(fallbackField);
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Unknown Staff";
        }
        if (id == null || id.trim().isEmpty()) {
            id = name;
        }
        return new CashierInfo(id, name);
    }

    private CashierStats statsFor(@NonNull Map<String, CashierStats> map, @NonNull CashierInfo info) {
        CashierStats stats = map.get(info.id);
        if (stats == null) {
            stats = new CashierStats(info.id, info.name);
            map.put(info.id, stats);
        }
        return stats;
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

    public static final class CashierInfo {
        public final String id;
        public final String name;

        public CashierInfo(String id, String name) {
            this.id = id;
            this.name = name;
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
