package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.RewardLogsRepository;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RewardLogsViewModel extends ViewModel {
    private final RewardLogsRepository repository;

    private final MutableLiveData<List<DocumentSnapshot>> redemptions = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public RewardLogsViewModel(RewardLogsRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<DocumentSnapshot>> getRedemptions() { return redemptions; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadRedemptions() {
        loading.setValue(true);
        repository.getRedemptionLogs()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                redemptions.setValue(queryDocumentSnapshots.getDocuments());
                loading.setValue(false);
            })
            .addOnFailureListener(e -> {
                error.setValue(e.getMessage());
                loading.setValue(false);
            });
    }
}
