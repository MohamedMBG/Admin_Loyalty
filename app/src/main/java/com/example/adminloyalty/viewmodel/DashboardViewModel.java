package com.example.adminloyalty.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.DashboardRepository;
import com.example.adminloyalty.data.DashboardRepository.CashierStats;
import com.example.adminloyalty.data.DashboardRepository.DashboardData;
import com.example.adminloyalty.data.DashboardRepository.DashboardPeriod;

import java.util.List;

public class DashboardViewModel extends ViewModel {

    private final DashboardRepository repository;

    private final MutableLiveData<DashboardUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public DashboardViewModel() {
        this(new DashboardRepository());
    }

    public DashboardViewModel(@NonNull DashboardRepository repository) {
        this.repository = repository;
    }

    public LiveData<DashboardUiState> getUiState() {
        return uiState;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void selectPeriod(@NonNull DashboardPeriod period) {
        loading.setValue(true);
        repository.loadDashboard(period, new DashboardRepository.DashboardCallback() {
            @Override
            public void onSuccess(@NonNull DashboardData data, boolean fromCache) {
                DashboardUiState state = new DashboardUiState(
                        data.period,
                        data.revenue,
                        data.previousRevenue,
                        data.points,
                        data.uniqueVisits,
                        data.chartData,
                        data.totalCostPoints,
                        data.gifts,
                        data.newClients,
                        data.cashiers
                );
                uiState.postValue(state);
                loading.postValue(false);
            }

            @Override
            public void onError(@NonNull String message) {
                error.postValue(message);
                loading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        repository.shutdown();
        super.onCleared();
    }

    public static final class DashboardUiState {
        public final DashboardPeriod period;
        public final double revenue;
        public final double previousRevenue;
        public final long points;
        public final int uniqueVisits;
        public final int[] chartData;
        public final double totalCostPoints;
        public final int gifts;
        public final long newClients;
        public final List<CashierStats> cashiers;

        public DashboardUiState(@NonNull DashboardPeriod period,
                                double revenue,
                                double previousRevenue,
                                long points,
                                int uniqueVisits,
                                @NonNull int[] chartData,
                                double totalCostPoints,
                                int gifts,
                                long newClients,
                                @NonNull List<CashierStats> cashiers) {
            this.period = period;
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
}
