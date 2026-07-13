package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.PromotionsRepository;
import com.example.adminloyalty.models.Promotion;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class PromotionsViewModel extends ViewModel {

    private final PromotionsRepository repository;
    private ListenerRegistration promotionsListener;

    private final MutableLiveData<List<Promotion>> promotions = new MutableLiveData<>();
    private final MutableLiveData<String> actionStatus = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public PromotionsViewModel(PromotionsRepository repository) {
        this.repository = repository;
        loadPromotions();
    }

    public LiveData<List<Promotion>> getPromotions() { return promotions; }
    public LiveData<String> getActionStatus() { return actionStatus; }
    public LiveData<String> getError() { return error; }

    private void loadPromotions() {
        promotionsListener = repository.getPromotionsQuery().addSnapshotListener((snapshots, e) -> {
            if (e != null || snapshots == null) {
                error.setValue("Failed to load promotions");
                return;
            }
            List<Promotion> list = new ArrayList<>();
            for (DocumentSnapshot doc : snapshots) {
                try {
                    Promotion p = doc.toObject(Promotion.class);
                    if (p != null) {
                        p.setId(doc.getId());
                        list.add(p);
                    }
                } catch (RuntimeException ignored) {
                    // Skip a malformed doc instead of crashing the screen.
                }
            }
            promotions.setValue(list);
        });
    }

    public void addPromotion(Promotion promotion) {
        repository.addPromotion(promotion)
                .addOnSuccessListener(ref -> actionStatus.setValue("Promo Live!"))
                .addOnFailureListener(e -> error.setValue("Failed to add promotion"));
    }

    public void updatePromotionStatus(String id, boolean isActive) {
        repository.updatePromotionStatus(id, isActive)
                .addOnFailureListener(e -> error.setValue("Failed to update status"));
    }

    public void deletePromotion(String id) {
        repository.deletePromotion(id)
                .addOnSuccessListener(aVoid -> actionStatus.setValue("Promotion Deleted"))
                .addOnFailureListener(e -> error.setValue("Failed to delete promotion"));
    }

    public void resetStatus() {
        actionStatus.setValue(null);
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (promotionsListener != null) {
            promotionsListener.remove();
        }
    }
}
