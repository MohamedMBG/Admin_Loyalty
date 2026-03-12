package com.example.adminloyalty.viewmodel;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.RedeemingRepository;
import com.example.adminloyalty.models.RewardItem;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RedeemingViewModel extends ViewModel {

    private final RedeemingRepository repository;

    private final MutableLiveData<String> cashierId = new MutableLiveData<>();
    private final MutableLiveData<String> cashierName = new MutableLiveData<>();

    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> success = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>(false);

    private final MutableLiveData<DocumentSnapshot> selectedUser = new MutableLiveData<>();
    private final MutableLiveData<String> eligiblePromoTitle = new MutableLiveData<>();
    private final MutableLiveData<String> eligiblePromoId = new MutableLiveData<>();

    private final MutableLiveData<List<RewardItem>> rewardsList = new MutableLiveData<>();

    @Inject
    public RedeemingViewModel(RedeemingRepository repository) {
        this.repository = repository;
        initCashierMeta();
        loadRewardsFromCatalog();
    }

    public LiveData<String> getError() { return error; }
    public LiveData<String> getSuccess() { return success; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Boolean> getIsSearching() { return isSearching; }
    public LiveData<DocumentSnapshot> getSelectedUser() { return selectedUser; }
    public LiveData<String> getEligiblePromoTitle() { return eligiblePromoTitle; }
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

    public void searchUser(String query) {
        if (query.isEmpty()) {
            selectedUser.setValue(null);
            return;
        }

        isSearching.setValue(true);
        String cleanQuery = query.trim();

        if (cleanQuery.contains("@")) {
            repository.searchUserByEmail(cleanQuery)
                    .addOnSuccessListener(this::handleUserResult)
                    .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
            return;
        }

        String formattedPhone = cleanQuery;
        String temp = cleanQuery.replace(" ", "");
        if (temp.matches("^0[567]\\d{8}$")) formattedPhone = "+212 " + temp.substring(1);
        else if (temp.matches("^[567]\\d{8}$")) formattedPhone = "+212 " + temp;

        repository.searchUserByPhone(formattedPhone)
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        handleUserResult(snap);
                    } else {
                        repository.searchUserByUid(cleanQuery)
                                .addOnSuccessListener(snap2 -> {
                                    if (!snap2.isEmpty()) {
                                        handleUserResult(snap2);
                                    } else {
                                        repository.searchUserByName(cleanQuery)
                                                .addOnSuccessListener(this::handleUserResult)
                                                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
                                    }
                                })
                                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
    }

    private void handleUserResult(QuerySnapshot querySnapshot) {
        isSearching.setValue(false);

        if (querySnapshot == null || querySnapshot.isEmpty()) {
            selectedUser.setValue(null);
            error.setValue("No user found.");
            return;
        }

        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
        selectedUser.setValue(doc);
        verifySystemPromotions(doc);
    }

    private void onSearchFailed(String msg) {
        isSearching.setValue(false);
        error.setValue("Search failed" + (msg != null ? (": " + msg) : ""));
    }

    private void verifySystemPromotions(DocumentSnapshot userDoc) {
        eligiblePromoTitle.setValue(null);
        eligiblePromoId.setValue(null);

        repository.getActivePromotions()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        return; // No active offers
                    }

                    List<DocumentSnapshot> eligiblePromos = new ArrayList<>();
                    for (DocumentSnapshot promo : snapshots.getDocuments()) {
                        if (runVerificationLogic(promo, userDoc)) eligiblePromos.add(promo);
                    }

                    if (eligiblePromos.isEmpty()) {
                        return; // No eligible offers
                    }

                    Collections.sort(eligiblePromos, (p1, p2) -> {
                        long prio1 = p1.getLong("priority") != null ? p1.getLong("priority") : 0;
                        long prio2 = p2.getLong("priority") != null ? p2.getLong("priority") : 0;
                        return Long.compare(prio2, prio1);
                    });

                    DocumentSnapshot topPromo = eligiblePromos.get(0);
                    eligiblePromoId.setValue(topPromo.getId());
                    String title = topPromo.getString("title");
                    eligiblePromoTitle.setValue(TextUtils.isEmpty(title) ? "Special offer" : title);
                });
    }

    private boolean runVerificationLogic(DocumentSnapshot promo, DocumentSnapshot userDoc) {
        if (promo == null || userDoc == null) return false;

        Timestamp start = promo.getTimestamp("startDate");
        Timestamp end = promo.getTimestamp("endDate");
        Timestamp now = Timestamp.now();

        if (start != null && now.compareTo(start) < 0) return false;
        if (end != null && now.compareTo(end) > 0) return false;

        String criteria = promo.getString("criteria");
        String ruleValue = promo.getString("value");

        if (criteria == null) return false;

        String gender = userDoc.getString("gender");
        String birthday = userDoc.getString("birthday");
        String address = userDoc.getString("address");
        Long ptsL = userDoc.getLong("points");
        int points = ptsL != null ? ptsL.intValue() : 0;
        Timestamp lastVisit = userDoc.getTimestamp("lastVisitTimestamp");

        switch (criteria) {
            case "ALL":
                return true;

            case "GENDER":
                return gender != null && ruleValue != null && gender.equalsIgnoreCase(ruleValue);

            case "AGE_UNDER":
                if (birthday == null || ruleValue == null) return false;
                int age = calculateAge(birthday);
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return age != -1 && age < limit;
                } catch (NumberFormatException e) {
                    return false;
                }

            case "LOCATION_CONTAINS":
                return address != null && ruleValue != null && address.toLowerCase().contains(ruleValue.toLowerCase());

            case "POINTS_UNDER":
                if (ruleValue == null) return false;
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return points < limit;
                } catch (NumberFormatException e) {
                    return false;
                }

            case "NO_VISIT_DAYS":
                if (ruleValue == null) return false;
                try {
                    int daysThreshold = Integer.parseInt(ruleValue);
                    if (lastVisit == null) return true;

                    long lastVisitMillis = lastVisit.toDate().getTime();
                    long diffDays = (System.currentTimeMillis() - lastVisitMillis) / (1000L * 60 * 60 * 24);
                    return diffDays > daysThreshold;
                } catch (NumberFormatException e) {
                    return false;
                }

            default:
                return false;
        }
    }

    private int calculateAge(String dobStr) {
        try {
            String[] parts = dobStr.split("-");
            Calendar dob = Calendar.getInstance();
            dob.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--;
            return age;
        } catch (Exception e) {
            return -1;
        }
    }

    public void redeemItem(String selectedItemDocId, String selectedItemName, int selectedItemCost) {
        DocumentSnapshot userDoc = selectedUser.getValue();
        if (userDoc == null) {
            error.setValue("Select customer first");
            return;
        }

        if (selectedItemName == null || selectedItemCost <= 0) {
            error.setValue("Select a reward first");
            return;
        }

        Long currentPts = userDoc.getLong("points");
        int points = currentPts != null ? currentPts.intValue() : 0;

        if (points < selectedItemCost) {
            error.setValue("Not enough points");
            return;
        }

        isLoading.setValue(true);

        repository.createRedeemCode(
                userDoc.getId(),
                userDoc.getString("uid"),
                userDoc.getString("fullName"),
                selectedItemDocId,
                selectedItemName,
                eligiblePromoId.getValue(),
                cashierId.getValue(),
                cashierName.getValue()
        ).addOnSuccessListener(payload -> {
            isLoading.setValue(false);
            success.setValue(payload);
        }).addOnFailureListener(e -> {
            isLoading.setValue(false);
            error.setValue("Redemption failed: " + e.getMessage());
        });
    }

    public void clearStatus() {
        error.setValue(null);
        success.setValue(null);
    }
}
