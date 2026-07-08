package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.RedeemingRepository;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;
import com.example.adminloyalty.models.AdminUser;
import com.example.adminloyalty.models.RewardItem;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RedeemingViewModel extends ViewModel {

    private final RedeemingRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<String> cashierId = new MutableLiveData<>();
    private final MutableLiveData<String> cashierName = new MutableLiveData<>();

    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> success = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>(false);

    private final MutableLiveData<AdminUser> selectedUser = new MutableLiveData<>();

    private final MutableLiveData<List<RewardItem>> rewardsList = new MutableLiveData<>();

    @Inject
    public RedeemingViewModel(RedeemingRepository repository, @IoExecutor ExecutorService io) {
        this.repository = repository;
        this.io = io;
        initCashierMeta();
        loadRewardsFromCatalog();
    }

    public LiveData<String> getError() { return error; }
    public LiveData<String> getSuccess() { return success; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Boolean> getIsSearching() { return isSearching; }
    public LiveData<AdminUser> getSelectedUser() { return selectedUser; }
    public LiveData<List<RewardItem>> getRewardsList() { return rewardsList; }

    private void initCashierMeta() {
        String uid = repository.getCurrentUserId();
        if (uid != null) {
            cashierId.setValue(uid);
            String email = repository.getCurrentUserEmail();
            cashierName.setValue((email != null && !email.trim().isEmpty()) ? email : "Unknown Cashier");

            repository.getUserProfile(uid).addOnSuccessListener(s -> {
                if (s.exists() && s.getString("fullName") != null) cashierName.setValue(s.getString("fullName"));
            });
        }
    }

    private void loadRewardsFromCatalog() {
        repository.getRewardsCatalog()
                .addOnSuccessListener(snapshots -> {
                    List<RewardItem> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        RewardItem item = doc.toObject(RewardItem.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            items.add(item);
                        }
                    }
                    rewardsList.setValue(items);
                })
                .addOnFailureListener(e -> error.setValue("Failed to load rewards"));
    }

    /**
     * Look up a customer by exact email or phone through the backend. Firestore rules deny admins
     * any direct read of another user's profile, so this replaces the old client-side Firestore
     * queries. The backend supports email OR phone only — the previous uid / name fallbacks are
     * gone (unsupported by the endpoint and rules-denied anyway).
     */
    public void searchUser(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            selectedUser.setValue(null);
            return;
        }

        isSearching.setValue(true);

        final String email;
        final String phone;
        if (cleanQuery.contains("@")) {
            email = cleanQuery;
            phone = null;
        } else {
            email = null;
            String temp = cleanQuery.replace(" ", "");
            if (temp.matches("^0[567]\\d{8}$")) phone = "+212 " + temp.substring(1);
            else if (temp.matches("^[567]\\d{8}$")) phone = "+212 " + temp;
            else phone = cleanQuery;
        }

        io.execute(() -> {
            ApiResult result = repository.searchUser(email, phone);
            isSearching.postValue(false);

            if (!result.isOk()) {
                selectedUser.postValue(null);
                error.postValue(mapSearchError(result));
                return;
            }

            JSONArray users = result.data != null ? result.data.optJSONArray("users") : null;
            JSONObject first = users != null ? users.optJSONObject(0) : null;
            if (first == null) { // empty, missing, or a non-object element — treat as no match
                selectedUser.postValue(null);
                error.postValue("No user found.");
                return;
            }
            selectedUser.postValue(AdminUser.fromJson(first));
        });
    }

    /**
     * Complete a customer's pending redemption from a scanned code via the backend.
     * The cashier no longer picks the reward or deducts points — the customer created the
     * pending redeem in their app; this just marks it fulfilled.
     */
    public void completeRedeem(String code) {
        if (code == null || code.trim().isEmpty()) {
            error.setValue("No code scanned");
            return;
        }
        final String clean = code.trim();

        isLoading.setValue(true);
        io.execute(() -> {
            ApiResult result = repository.completeRedeem(clean);
            isLoading.postValue(false);
            if (result.isOk()) {
                String rewardName = result.data != null ? result.data.optString("rewardName", "reward") : "reward";
                String status = result.data != null ? result.data.optString("status", "completed") : "completed";
                success.postValue(rewardName + " · " + status);
            } else {
                error.postValue(mapError(result));
            }
        });
    }

    private String mapSearchError(ApiResult r) {
        if ("SEARCH_CRITERIA_REQUIRED".equals(r.code)) return "Enter an email or phone to search.";
        return ApiErrors.message(r, "Not authorized. Admin role required.", "Search failed");
    }

    private String mapError(ApiResult r) {
        if ("REDEEM_NOT_FOUND".equals(r.code)) return "Code not found. Ask the customer to reopen their reward.";
        if ("REDEEM_NOT_PENDING".equals(r.code)) return "This code is not pending — already used, canceled, or expired.";
        return ApiErrors.message(r, "Not authorized. Cashier role required.", "Redemption failed");
    }

    public void clearStatus() {
        error.setValue(null);
        success.setValue(null);
    }
}
