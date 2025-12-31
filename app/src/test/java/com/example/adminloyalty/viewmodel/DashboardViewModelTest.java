package com.example.adminloyalty.viewmodel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.adminloyalty.data.DashboardRepository;
import com.example.adminloyalty.data.DashboardRepository.CashierStats;
import com.example.adminloyalty.data.DashboardRepository.DashboardData;
import com.example.adminloyalty.data.DashboardRepository.DashboardPeriod;
import com.example.adminloyalty.data.DashboardRepository.DateRange;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Date;

public class DashboardViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private DashboardRepository repository;
    private DashboardViewModel viewModel;

    @Before
    public void setUp() {
        repository = mock(DashboardRepository.class);
        viewModel = new DashboardViewModel(repository);
    }

    @Test
    public void selectPeriod_emitsUiStateOnSuccess() {
        viewModel.selectPeriod(DashboardPeriod.WEEK);

        ArgumentCaptor<DashboardRepository.DashboardCallback> captor =
                ArgumentCaptor.forClass(DashboardRepository.DashboardCallback.class);
        verify(repository).loadDashboard(eq(DashboardPeriod.WEEK), captor.capture());

        assertEquals(Boolean.TRUE, viewModel.getLoading().getValue());

        CashierStats stats = new CashierStats("id-1", "Alice");
        stats.scans = 2;
        int[] chart = new int[]{1, 2, 0, 0, 0, 0, 0};
        DashboardData data = new DashboardData(
                DashboardPeriod.WEEK,
                new DateRange(new Date(0), new Date(1)),
                120.0,
                80.0,
                45L,
                10,
                chart,
                15.0,
                3,
                5L,
                Arrays.asList(stats)
        );

        captor.getValue().onSuccess(data, false);

        DashboardViewModel.DashboardUiState uiState = viewModel.getUiState().getValue();
        assertNotNull(uiState);
        assertEquals(DashboardPeriod.WEEK, uiState.period);
        assertEquals(120.0, uiState.revenue, 0.0001);
        assertEquals(45L, uiState.points);
        assertArrayEquals(chart, uiState.chartData);
        assertEquals(10, uiState.uniqueVisits);
        assertEquals(false, viewModel.getLoading().getValue());
        assertNull(viewModel.getError().getValue());
    }

    @Test
    public void selectPeriod_emitsError() {
        viewModel.selectPeriod(DashboardPeriod.MONTH);

        ArgumentCaptor<DashboardRepository.DashboardCallback> captor =
                ArgumentCaptor.forClass(DashboardRepository.DashboardCallback.class);
        verify(repository).loadDashboard(eq(DashboardPeriod.MONTH), captor.capture());

        captor.getValue().onError("failure");

        assertEquals("failure", viewModel.getError().getValue());
        assertEquals(false, viewModel.getLoading().getValue());
    }

    @Test
    public void onCleared_shutsRepository() {
        viewModel.onCleared();

        verify(repository).shutdown();
    }
}
