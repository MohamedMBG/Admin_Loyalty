package com.example.adminloyalty.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Unit tests for {@link DashboardRepository} business aggregation logic.
 */
public class DashboardRepositoryTest {

    private DashboardRepository repository;
    private DashboardRepository.DateRange range;

    @Before
    public void setUp() {
        repository = new DashboardRepository(mock(FirebaseFirestore.class));

        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.OCTOBER, 14, 0, 0, 0); // Monday
        Date start = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 7);
        Date end = cal.getTime();
        range = new DashboardRepository.DateRange(start, end);
    }

    @Test
    public void buildDashboardData_aggregatesRevenuePointsAndStats() {
        Timestamp mondayMorning = new Timestamp(new Date(range.start.getTime() + hours(10)));
        Timestamp tuesdayNoon = new Timestamp(new Date(range.start.getTime() + days(1) + hours(12)));
        Timestamp tuesdayLater = new Timestamp(new Date(range.start.getTime() + days(1) + hours(18)));

        List<DocumentSnapshot> earnDocs = new ArrayList<>();
        earnDocs.add(mockEarnDoc("e1", 50.0, 20L, mondayMorning, "u1", "cash-1", "Alice", "Creator A"));
        earnDocs.add(mockEarnDoc("e2", 30.0, 10L, tuesdayNoon, "u1", "cash-1", "Alice", "Creator A"));
        earnDocs.add(mockEarnDoc("e3", 20.0, 5L, tuesdayLater, "u1", "cash-1", "", "Helper"));

        List<DocumentSnapshot> previousDocs = new ArrayList<>();
        previousDocs.add(mockPreviousRevenueDoc(40.0));
        previousDocs.add(mockPreviousRevenueDoc(5.0));

        List<DocumentSnapshot> redeemDocs = new ArrayList<>();
        redeemDocs.add(mockRedeemDoc("r1", 15.0, "cash-2", "Bob", "Processor"));
        redeemDocs.add(mockRedeemDoc("r2", 5.0, "cash-1", "", "Helper"));

        QuerySnapshot earnSnap = mockSnapshot(earnDocs);
        QuerySnapshot prevSnap = mockSnapshot(previousDocs);
        QuerySnapshot redeemSnap = mockSnapshot(redeemDocs);

        AggregateQuerySnapshot newClientsSnap = mock(AggregateQuerySnapshot.class);
        when(newClientsSnap.getCount()).thenReturn(3L);

        DashboardRepository.DashboardData data = repository.buildDashboardData(
                DashboardRepository.DashboardPeriod.WEEK,
                range,
                earnSnap,
                prevSnap,
                redeemSnap,
                newClientsSnap
        );

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
        assertEquals(3, top.scans);
        assertEquals(1, top.redeems);

        DashboardRepository.CashierStats second = data.cashiers.get(1);
        assertEquals("cash-2", second.id);
        assertEquals(0, second.scans);
        assertEquals(1, second.redeems);

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

    private long hours(int h) {
        return h * 60L * 60L * 1000L;
    }

    private long days(int d) {
        return d * 24L * 60L * 60L * 1000L;
    }

    private DocumentSnapshot mockEarnDoc(String id, double amount, long points, Timestamp createdAt,
                                         String uid, String cashierId, String cashierName, String createdBy) {
        DocumentSnapshot doc = mock(DocumentSnapshot.class);
        when(doc.getId()).thenReturn(id);
        when(doc.getDouble("amountMAD")).thenReturn(amount);
        when(doc.getLong("points")).thenReturn(points);
        when(doc.getTimestamp("createdAt")).thenReturn(createdAt);
        when(doc.getString("redeemedByUid")).thenReturn(uid);
        when(doc.getString("cashierId")).thenReturn(cashierId);
        when(doc.getString("cashierName")).thenReturn(cashierName);
        when(doc.getString("createdByName")).thenReturn(createdBy);
        return doc;
    }

    private DocumentSnapshot mockRedeemDoc(String id, double costPoints, String cashierId,
                                           String cashierName, String processedByName) {
        DocumentSnapshot doc = mock(DocumentSnapshot.class);
        when(doc.getId()).thenReturn(id);
        when(doc.getDouble("costPoints")).thenReturn(costPoints);
        when(doc.getString("cashierId")).thenReturn(cashierId);
        when(doc.getString("cashierName")).thenReturn(cashierName);
        when(doc.getString("processedByName")).thenReturn(processedByName);
        return doc;
    }

    private DocumentSnapshot mockPreviousRevenueDoc(double amount) {
        DocumentSnapshot doc = mock(DocumentSnapshot.class);
        when(doc.getDouble("amountMAD")).thenReturn(amount);
        return doc;
    }

    private QuerySnapshot mockSnapshot(List<DocumentSnapshot> docs) {
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        when(snapshot.getDocuments()).thenReturn(docs);
        when(snapshot.size()).thenReturn(docs.size());
        return snapshot;
    }
}
