package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.ClientDetailsRepository;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;
import com.example.adminloyalty.fragments.ClientDetailsFragment.ActivityItem;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ClientDetailsViewModel extends ViewModel {

    private static final int ACTIVITY_LIMIT = 50;

    private final ClientDetailsRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<DocumentSnapshot> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Double> averageSpend = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityItem>> allActivities = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingHistory = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> adjustSuccess = new MutableLiveData<>();

    @Inject
    public ClientDetailsViewModel(ClientDetailsRepository repository, @IoExecutor ExecutorService io) {
        this.repository = repository;
        this.io = io;
    }

    public LiveData<DocumentSnapshot> getUserProfile() { return userProfile; }
    public LiveData<Double> getAverageSpend() { return averageSpend; }
    public LiveData<List<ActivityItem>> getAllActivities() { return allActivities; }
    public LiveData<Boolean> getLoadingHistory() { return loadingHistory; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getAdjustSuccess() { return adjustSuccess; }

    public void loadClientData(String clientId) {
        if (clientId == null) return;
        loadUserProfile(clientId);
        loadHistory(clientId);
    }

    /** Apply a points adjustment through the backend, then refresh the profile on success. */
    public void adjustPoints(String clientId, int delta, String reason) {
        if (clientId == null) { error.setValue("No client loaded"); return; }
        if (delta == 0) { error.setValue("Enter a non-zero amount"); return; }
        if (reason == null || reason.trim().isEmpty()) { error.setValue("Reason is required"); return; }
        final String cleanReason = reason.trim();

        io.execute(() -> {
            ApiResult result = repository.adjustPoints(clientId, delta, cleanReason);
            if (result.isOk()) {
                adjustSuccess.postValue((delta > 0 ? "+" : "") + delta + " pts applied");
                loadUserProfile(clientId); // reflect the new balance
            } else {
                error.postValue(mapError(result));
            }
        });
    }

    private String mapError(ApiResult r) {
        if ("USER_NOT_FOUND".equals(r.code)) return "User not found.";
        return ApiErrors.message(r, "Not authorized. Admin role required.", "Adjustment failed");
    }

    public void clearAdjustStatus() {
        adjustSuccess.setValue(null);
        error.setValue(null);
    }

    private void loadUserProfile(String clientId) {
        repository.getUserProfile(clientId).addOnSuccessListener(document -> {
            if (document.exists()) {
                userProfile.postValue(document);
            } else {
                error.postValue("User not found");
            }
        }).addOnFailureListener(e -> error.postValue("Error loading profile"));

        // ponytail: avg spend was derived from earn_codes.amountMAD — a field the backend no longer
        // exposes (earn codes are points-direct now). No backend analytics endpoint for it, so it's
        // zeroed. Restore when a spend endpoint exists.
        averageSpend.postValue(0.0);
    }

    private void loadHistory(String clientId) {
        loadingHistory.postValue(true);
        io.execute(() -> {
            ApiResult result = repository.getUserActivity(clientId, ACTIVITY_LIMIT);
            if (!result.isOk()) {
                error.postValue(mapError(result));
                loadingHistory.postValue(false);
                return;
            }

            List<ActivityItem> activities = new ArrayList<>();
            JSONArray entries = result.data != null ? result.data.optJSONArray("activities") : null;
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.optJSONObject(i);
                    if (e == null) continue;
                    long delta = e.optLong("pointsDelta", 0);
                    long createdAt = e.optLong("createdAt", 0);
                    // The backend feed carries no cashier attribution or reward name; the UI only
                    // distinguishes credit (green +) from debit (red −), so collapse by sign.
                    String type = delta >= 0 ? "earn" : "spend";
                    Timestamp ts = createdAt > 0 ? new Timestamp(new Date(createdAt)) : null;
                    activities.add(new ActivityItem(type, Math.abs(delta), null, null, ts));
                }
            }
            allActivities.postValue(activities);
            loadingHistory.postValue(false);
        });
    }
}
