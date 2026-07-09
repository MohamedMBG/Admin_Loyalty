package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.RewardsAdminRepository;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;
import com.example.adminloyalty.models.RewardItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RewardsAdminViewModel extends ViewModel {

    private final RewardsAdminRepository repository;
    private final ExecutorService io;
    private ListenerRegistration listener;

    private final MutableLiveData<List<RewardItem>> rewards = new MutableLiveData<>();
    private final MutableLiveData<String> actionStatus = new MutableLiveData<>();

    @Inject
    public RewardsAdminViewModel(RewardsAdminRepository repository, @IoExecutor ExecutorService io) {
        this.repository = repository;
        this.io = io;
        loadRewards();
    }

    public LiveData<List<RewardItem>> getRewards() { return rewards; }
    public LiveData<String> getActionStatus() { return actionStatus; }

    private void loadRewards() {
        listener = repository.getRewardsQuery().addSnapshotListener((snapshots, e) -> {
            if (e != null || snapshots == null) return;
            List<RewardItem> list = new ArrayList<>();
            for (DocumentSnapshot doc : snapshots) {
                RewardItem item = doc.toObject(RewardItem.class);
                if (item != null) {
                    item.setId(doc.getId());
                    list.add(item);
                }
            }
            rewards.setValue(list);
        });
    }

    public void addReward(RewardItem item) {
        io.execute(() -> {
            ApiResult r = repository.addReward(item);
            actionStatus.postValue(r.isOk() ? "Reward Added" : mapError(r));
        });
    }

    public void updateReward(String id, RewardItem item) {
        io.execute(() -> {
            ApiResult r = repository.updateReward(id, item);
            actionStatus.postValue(r.isOk() ? "Reward Updated" : mapError(r));
        });
    }

    public void deleteReward(String id) {
        io.execute(() -> {
            ApiResult r = repository.deleteReward(id);
            actionStatus.postValue(r.isOk() ? "Reward Deleted" : mapError(r));
        });
    }

    private String mapError(ApiResult r) {
        if ("INVALID_REWARD".equals(r.code)) return "Check the reward name and points.";
        if ("REWARD_NOT_FOUND".equals(r.code)) return "That reward no longer exists.";
        return ApiErrors.message(r, "Not authorized. Admin role required.", "Action failed");
    }

    public void resetStatus() {
        actionStatus.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null) listener.remove();
    }
}
