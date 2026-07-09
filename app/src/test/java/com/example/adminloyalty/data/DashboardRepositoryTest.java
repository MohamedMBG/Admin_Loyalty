package com.example.adminloyalty.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

/**
 * Unit tests for {@link DashboardRepository}: mapping the backend analytics JSON onto DashboardData,
 * and the previous-period range math. The aggregation itself now lives in the backend AnalyticsService.
 * Static helpers only — no Android Context, so it runs on a plain JVM.
 */
public class DashboardRepositoryTest {

    private DashboardRepository.DateRange range;

    @Before
    public void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.OCTOBER, 14, 0, 0, 0); // Monday
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        Date end = cal.getTime();
        range = new DashboardRepository.DateRange(start, end);
    }

    @Test
    public void parseAnalytics_mapsResponseFieldsAndCashiers() throws Exception {
        JSONObject json = new JSONObject()
                .put("revenue", 100.0)
                .put("pointsIssued", 35)
                .put("pointsRedeemed", 20)
                .put("gifts", 2)
                .put("newClients", 3)
                .put("uniqueVisitors", 2)
                .put("series", new JSONArray()
                        .put(new JSONObject().put("earnCount", 1))
                        .put(new JSONObject().put("earnCount", 2)))
                .put("cashiers", new JSONArray()
                        .put(new JSONObject().put("cashierUid", "cash-1").put("cashierName", "Alice")
                                .put("codesIssued", 3).put("redeemsCompleted", 1))
                        .put(new JSONObject().put("cashierUid", "cash-2").put("cashierName", "Bob")
                                .put("codesIssued", 0).put("redeemsCompleted", 1)));

        DashboardRepository.DashboardData data = DashboardRepository.parseAnalytics(
                DashboardRepository.DashboardPeriod.WEEK, range, json, 45.0);

        assertNotNull(data);
        assertEquals(100.0, data.revenue, 0.0001);
        assertEquals(45.0, data.previousRevenue, 0.0001);
        assertEquals(35L, data.points);
        assertEquals(2, data.uniqueVisits);
        assertEquals(20.0, data.totalCostPoints, 0.0001);
        assertEquals(2, data.gifts);
        assertEquals(3L, data.newClients);

        assertEquals(2, data.cashiers.size());
        DashboardRepository.CashierStats top = data.cashiers.get(0);
        assertEquals("cash-1", top.id);
        assertEquals("Alice", top.name);
        assertEquals(3, top.scans);
        assertEquals(1, top.redeems);

        int[] expectedChart = new int[]{1, 2, 0, 0, 0, 0, 0};
        assertArrayEquals(expectedChart, data.chartData);
    }

    @Test
    public void dateRange_previousOfMatchesDuration() {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 10, 0, 0, 0);
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 3);
        Date end = cal.getTime();

        DashboardRepository.DateRange previous = DashboardRepository.DateRange.previousOf(start, end);

        assertEquals(3 * 24 * 60 * 60 * 1000L, end.getTime() - start.getTime());
        assertEquals(start, previous.end);
        assertFalse(previous.start.after(previous.end));
    }
}
