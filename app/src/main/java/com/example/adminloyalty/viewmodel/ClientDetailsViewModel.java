package com.example.adminloyalty.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.ClientDetailsRepository;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;
import com.example.adminloyalty.fragments.ClientDetailsFragment.ActivityItem;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ClientDetailsViewModel extends ViewModel {

    private final ClientDetailsRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<DocumentSnapshot> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Double> averageSpend = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityItem>> allActivities = new MutableLiveData<>();
    private final MutableLiveData<List<String>> cashiers = new MutableLiveData<>();
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
    public LiveData<List<String>> getCashiers() { return cashiers; }
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
        if (r.code == null) return "Request failed";
        switch (r.code) {
            case "NETWORK_ERROR":  return "No connection. Check your network and retry.";
            case "CLIENT_ERROR":   return "Could not build the request.";
            case "USER_NOT_FOUND": return "User not found.";
            case "FORBIDDEN":
            case "HTTP_403":       return "Not authorized. Admin role required.";
            case "RATE_LIMITED":   return "Too many requests. Try again shortly.";
            default:               return r.message != null ? r.message : "Adjustment failed";
        }
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

        repository.getUserEarnCodes(clientId).addOnSuccessListener(scans -> {
            double totalSpendMAD = 0.0;
            int visitCount = scans.size();

            for (DocumentSnapshot scan : scans) {
                Double amount = scan.getDouble("amountMAD");
                if (amount != null) {
                    totalSpendMAD += amount;
                }
            }

            double avgSpend = 0.0;
            if (visitCount > 0) {
                avgSpend = totalSpendMAD / visitCount;
            }
            averageSpend.postValue(avgSpend * 0.5);
        }).addOnFailureListener(e -> averageSpend.postValue(0.0));
    }

    private void loadHistory(String clientId) {
        loadingHistory.postValue(true);
        Task<QuerySnapshot> earnTask = repository.getUserEarnCodes(clientId);
        Task<QuerySnapshot> spendTask = repository.getUserRedeemCodes(clientId);

        Tasks.whenAllSuccess(earnTask, spendTask).addOnSuccessListener(results -> {
            List<ActivityItem> activities = new ArrayList<>();

            QuerySnapshot earnSnap = (QuerySnapshot) results.get(0);
            for (DocumentSnapshot doc : earnSnap.getDocuments()) {
                try {
                    Long pts = doc.getLong("points");
                    long points = pts != null ? pts : 0;
                    String cashier = doc.getString("cashierName");
                    Timestamp ts = doc.getTimestamp("redeemedAt");

                    activities.add(new ActivityItem("earn", points, null, cashier, ts));
                } catch (Exception e) {
                    Log.e("ClientDetailsVM", "Error parsing earn log", e);
                }
            }

            QuerySnapshot spendSnap = (QuerySnapshot) results.get(1);
            for (DocumentSnapshot doc : spendSnap.getDocuments()) {
                try {
                    Long pts = doc.getLong("costPoints");
                    long points = pts != null ? pts : 0;
                    String itemName = doc.getString("itemName");
                    String cashier = doc.getString("cashierName");
                    Timestamp ts = doc.getTimestamp("completedAt");

                    activities.add(new ActivityItem("spend", points, itemName, cashier, ts));
                } catch (Exception e) {
                    Log.e("ClientDetailsVM", "Error parsing spend log", e);
                }
            }

            allActivities.postValue(activities);
            loadingHistory.postValue(false);
        }).addOnFailureListener(e -> {
            error.postValue("Failed to load history");
            loadingHistory.postValue(false);
        });
    }

    public void fetchCashiers() {
        repository.getCashiers().addOnSuccessListener(queryDocumentSnapshots -> {
            List<String> cashierNames = new ArrayList<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String name = doc.getString("fullName");
                if (name == null) name = doc.getString("name");
                if (name != null && !name.isEmpty()) {
                    cashierNames.add(name);
                }
            }
            Collections.sort(cashierNames);
            cashiers.postValue(cashierNames);
        }).addOnFailureListener(e -> error.postValue("Failed to load cashiers: " + e.getMessage()));
    }
}
