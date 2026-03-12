package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.RewardsAdminRepository;
import com.example.adminloyalty.models.RewardItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RewardsAdminViewModel extends ViewModel {

    private final RewardsAdminRepository repository;
    private ListenerRegistration listener;

    private final MutableLiveData<List<RewardItem>> rewards = new MutableLiveData<>();
    private final MutableLiveData<String> actionStatus = new MutableLiveData<>();

    @Inject
    public RewardsAdminViewModel(RewardsAdminRepository repository) {
        this.repository = repository;
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
        repository.addReward(item).addOnSuccessListener(ref -> actionStatus.setValue("Reward Added"));
    }

    public void updateReward(String id, RewardItem item) {
        repository.updateReward(id, item).addOnSuccessListener(aVoid -> actionStatus.setValue("Reward Updated"));
    }

    public void deleteReward(String id) {
        repository.deleteReward(id).addOnSuccessListener(aVoid -> actionStatus.setValue("Reward Deleted"));
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
