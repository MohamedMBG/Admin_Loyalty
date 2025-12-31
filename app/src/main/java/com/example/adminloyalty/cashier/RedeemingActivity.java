package com.example.adminloyalty.cashier;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.adminloyalty.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedeemingActivity extends AppCompatActivity {

    // UI Views
    private EditText searchClientEt;
    private View emptyStateCard;
    private MaterialCardView clientCard;

    private TextView clientNameTv;
    private Chip pointsChip, eligibilityChip;
    private LinearProgressIndicator progressToReward;
    private TextView tvProgressHint;

    private MaterialCardView promoCard;
    private TextView tvOfferText;

    private TextView tvSelectedItem, tvRequiredPoints;
    private MaterialButton btnRedeem;

    // Expandable categories
    private LinearLayout headerHotCoffee, itemsHotCoffee;
    private LinearLayout headerIcedCoffee, itemsIcedCoffee;
    private LinearLayout headerTea, itemsTea;
    private LinearLayout headerFrappuccino, itemsFrappuccino;
    private LinearLayout headerPastries, itemsPastries;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String cashierId, cashierName, shopId;

    // Selected User Data
    private String selectedUserUid = null;
    private String selectedUserDocId = null;
    private String selectedUserName = null;
    private int selectedUserPoints = 0;

    // Verification Data
    private String selectedUserGender = null;
    private String selectedUserBirthday = null;
    private String selectedUserAddress = null;
    private Timestamp selectedUserLastVisit = null;

    // Selected reward
    private String selectedItemName = null;
    private int selectedItemCost = 0;

    // Debounce
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DEBOUNCE_MS = 350;
    private boolean isSearching = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redeeming);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        initCashierMeta();

        setupCategoryToggles();
        loadRewardsFromCatalog();

        setupSearch();
        setupRedeemButton();

        clearSelectedUserUI();
    }

    private void initViews() {
        searchClientEt = findViewById(R.id.searchClient);

        emptyStateCard = findViewById(R.id.emptyStateCard);
        clientCard = findViewById(R.id.clientCard);

        clientNameTv = findViewById(R.id.clientName);

        pointsChip = findViewById(R.id.pointsChip);
        eligibilityChip = findViewById(R.id.eligibilityChip);

        progressToReward = findViewById(R.id.progressToReward);
        tvProgressHint = findViewById(R.id.tvProgressHint);

        promoCard = findViewById(R.id.layoutPromoContainer);
        tvOfferText = findViewById(R.id.tvOfferText);

        tvSelectedItem = findViewById(R.id.tvSelectedItem);
        tvRequiredPoints = findViewById(R.id.tvRequiredPoints);
        btnRedeem = findViewById(R.id.btnRedeem);

        // Expandables (YOU MUST HAVE THESE IDS IN XML)
        headerHotCoffee = findViewById(R.id.headerHotCoffee);
        itemsHotCoffee = findViewById(R.id.itemsHotCoffee);

        headerIcedCoffee = findViewById(R.id.headerIcedCoffee);
        itemsIcedCoffee = findViewById(R.id.itemsIcedCoffee);

        headerTea = findViewById(R.id.headerTea);
        itemsTea = findViewById(R.id.itemsTea);

        headerFrappuccino = findViewById(R.id.headerFrappuccino);
        itemsFrappuccino = findViewById(R.id.itemsFrappuccino);

        headerPastries = findViewById(R.id.headerPastries);
        itemsPastries = findViewById(R.id.itemsPastries);
    }

    // -------------------------
    // SEARCH (debounced + enter)
    // -------------------------
    private void setupSearch() {
        searchClientEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        searchClientEt.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) {
                triggerSearchNow();
                return true;
            }
            return false;
        });

        searchClientEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                    clearSelectedUserUI();
                    return;
                }
                debounceSearch(q);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void debounceSearch(String q) {
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        searchRunnable = () -> searchUser(q);
        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void triggerSearchNow() {
        String q = searchClientEt.getText().toString().trim();
        if (q.isEmpty()) { clearSelectedUserUI(); return; }
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        searchUser(q);
    }

    private void searchUser(String query) {
        isSearching = true;
        updateRedeemButtonState();

        String cleanQuery = query.trim();

        // 1) Email
        if (cleanQuery.contains("@")) {
            db.collection("users").whereEqualTo("email", cleanQuery).limit(1)
                    .get()
                    .addOnSuccessListener(this::handleUserResult)
                    .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
            return;
        }

        // 2) Phone normalization (+212)
        String formattedPhone = cleanQuery;
        String temp = cleanQuery.replace(" ", "");
        if (temp.matches("^0[567]\\d{8}$")) formattedPhone = "+212 " + temp.substring(1);
        else if (temp.matches("^[567]\\d{8}$")) formattedPhone = "+212 " + temp;

        // phone -> uid -> fullName
        db.collection("users").whereEqualTo("phone", formattedPhone).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        handleUserResult(snap);
                    } else {
                        db.collection("users").whereEqualTo("uid", cleanQuery).limit(1).get()
                                .addOnSuccessListener(snap2 -> {
                                    if (!snap2.isEmpty()) {
                                        handleUserResult(snap2);
                                    } else {
                                        db.collection("users").whereEqualTo("fullName", cleanQuery).limit(1).get()
                                                .addOnSuccessListener(this::handleUserResult)
                                                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
                                    }
                                })
                                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> onSearchFailed(e.getMessage()));
    }

    private void onSearchFailed(String msg) {
        isSearching = false;
        showToast("Search failed" + (msg != null ? (": " + msg) : ""));
        updateRedeemButtonState();
    }

    private void handleUserResult(QuerySnapshot querySnapshot) {
        isSearching = false;

        if (querySnapshot == null || querySnapshot.isEmpty()) {
            clearSelectedUserUI();
            showToast("No user found.");
            return;
        }

        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

        selectedUserDocId = doc.getId();
        selectedUserUid = doc.getString("uid");
        selectedUserName = doc.getString("fullName");

        Long pts = doc.getLong("points");
        selectedUserPoints = pts != null ? pts.intValue() : 0;

        selectedUserGender = doc.getString("gender");
        selectedUserBirthday = doc.getString("birthday");
        selectedUserAddress = doc.getString("address");
        selectedUserLastVisit = doc.getTimestamp("lastVisitTimestamp");

        // UI
        if (emptyStateCard != null) emptyStateCard.setVisibility(View.GONE);
        clientCard.setVisibility(View.VISIBLE);

        clientNameTv.setText(!TextUtils.isEmpty(selectedUserName) ? selectedUserName : "Unknown User");

        pointsChip.setText(selectedUserPoints + " pts");
        setEligibilityNeutral();

        promoCard.setVisibility(View.GONE);
        tvOfferText.setText("");

        verifySystemPromotions();

        updateProgressUI();
        checkPointsAndWarn();
        updateRedeemButtonState();
    }

    private void clearSelectedUserUI() {
        selectedUserUid = null;
        selectedUserDocId = null;
        selectedUserName = null;
        selectedUserPoints = 0;

        selectedUserGender = null;
        selectedUserBirthday = null;
        selectedUserAddress = null;
        selectedUserLastVisit = null;

        clientCard.setVisibility(View.GONE);
        if (emptyStateCard != null) emptyStateCard.setVisibility(View.VISIBLE);

        pointsChip.setText("0 pts");
        setEligibilityNeutral();

        promoCard.setVisibility(View.GONE);
        tvOfferText.setText("");

        progressToReward.setProgress(0);
        tvProgressHint.setText("Select a reward to see required points.");

        // keep selection? I recommend RESET selection when customer cleared
        selectedItemName = null;
        selectedItemCost = 0;
        tvSelectedItem.setText("Select a reward");
        tvRequiredPoints.setText("-- pts required");
        tvRequiredPoints.setTextColor(Color.GRAY);

        isSearching = false;
        updateRedeemButtonState();
    }

    // -------------------------
    // PROMOS
    // -------------------------
    private void verifySystemPromotions() {
        promoCard.setVisibility(View.GONE);
        tvOfferText.setText("");

        db.collection("promotions")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        setEligibilityInfo("No active offers");
                        return;
                    }

                    List<DocumentSnapshot> eligiblePromos = new ArrayList<>();
                    for (DocumentSnapshot promo : snapshots) {
                        if (runVerificationLogic(promo)) eligiblePromos.add(promo);
                    }

                    if (eligiblePromos.isEmpty()) {
                        setEligibilityWarning("No eligible offers");
                        return;
                    }

                    Collections.sort(eligiblePromos, (p1, p2) -> {
                        long prio1 = p1.getLong("priority") != null ? p1.getLong("priority") : 0;
                        long prio2 = p2.getLong("priority") != null ? p2.getLong("priority") : 0;
                        return Long.compare(prio2, prio1);
                    });

                    DocumentSnapshot topPromo = eligiblePromos.get(0);
                    String title = topPromo.getString("title");
                    if (TextUtils.isEmpty(title)) title = "Special offer";

                    tvOfferText.setText(title);
                    promoCard.setVisibility(View.VISIBLE);

                    setEligibilitySuccess("Eligible offer available");
                })
                .addOnFailureListener(e -> setEligibilityInfo("Offer check failed"));
    }

    private boolean runVerificationLogic(DocumentSnapshot promo) {
        if (promo == null) return false;

        Timestamp start = promo.getTimestamp("startDate");
        Timestamp end = promo.getTimestamp("endDate");
        Timestamp now = Timestamp.now();

        if (start != null && now.compareTo(start) < 0) return false;
        if (end != null && now.compareTo(end) > 0) return false;

        String criteria = promo.getString("criteria");
        String ruleValue = promo.getString("value");

        if (criteria == null) return false;

        switch (criteria) {
            case "ALL":
                return true;

            case "GENDER":
                return selectedUserGender != null && ruleValue != null
                        && selectedUserGender.equalsIgnoreCase(ruleValue);

            case "AGE_UNDER":
                if (selectedUserBirthday == null || ruleValue == null) return false;
                int age = calculateAge(selectedUserBirthday);
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return age != -1 && age < limit;
                } catch (NumberFormatException e) {
                    return false;
                }

            case "LOCATION_CONTAINS":
                return selectedUserAddress != null && ruleValue != null
                        && selectedUserAddress.toLowerCase().contains(ruleValue.toLowerCase());

            case "POINTS_UNDER":
                if (ruleValue == null) return false;
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return selectedUserPoints < limit;
                } catch (NumberFormatException e) {
                    return false;
                }

            case "NO_VISIT_DAYS":
                if (ruleValue == null) return false;
                try {
                    int daysThreshold = Integer.parseInt(ruleValue);
                    if (selectedUserLastVisit == null) return true;

                    long lastVisitMillis = selectedUserLastVisit.toDate().getTime();
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

    // -------------------------
    // EXPANDABLE CATEGORIES
    // -------------------------
    private void setupCategoryToggles() {
        setupToggle(headerHotCoffee, itemsHotCoffee);
        setupToggle(headerIcedCoffee, itemsIcedCoffee);
        setupToggle(headerTea, itemsTea);
        setupToggle(headerFrappuccino, itemsFrappuccino);
        setupToggle(headerPastries, itemsPastries);
    }

    private void setupToggle(View header, View content) {
        if (header == null || content == null) return;

        header.setOnClickListener(v -> {
            int newVisibility = (content.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE;
            content.setVisibility(newVisibility);

            // tiny UX: scroll a bit when expanding
            if (newVisibility == View.VISIBLE) {
                content.post(() -> content.requestFocus());
            }
        });
    }

    // -------------------------
    // LOAD REWARDS INTO EXPANDABLE LISTS
    // -------------------------
    private void loadRewardsFromCatalog() {
        clearCategoryContainers();

        db.collection("rewards_catalog")
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) return;

                    for (DocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        String cat = doc.getString("category");
                        Long costL = doc.getLong("costPoints");
                        int cost = costL != null ? costL.intValue() : 0;

                        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(cat)) continue;

                        View itemView = createItemRow(name, cost);
                        addToContainer(cat, itemView);
                    }
                })
                .addOnFailureListener(e -> showToast("Failed to load rewards"));
    }

    private void clearCategoryContainers() {
        if (itemsHotCoffee != null) itemsHotCoffee.removeAllViews();
        if (itemsIcedCoffee != null) itemsIcedCoffee.removeAllViews();
        if (itemsTea != null) itemsTea.removeAllViews();
        if (itemsFrappuccino != null) itemsFrappuccino.removeAllViews();
        if (itemsPastries != null) itemsPastries.removeAllViews();

        // Keep them collapsed by default
        if (itemsHotCoffee != null) itemsHotCoffee.setVisibility(View.GONE);
        if (itemsIcedCoffee != null) itemsIcedCoffee.setVisibility(View.GONE);
        if (itemsTea != null) itemsTea.setVisibility(View.GONE);
        if (itemsFrappuccino != null) itemsFrappuccino.setVisibility(View.GONE);
        if (itemsPastries != null) itemsPastries.setVisibility(View.GONE);
    }

    private void addToContainer(String cat, View v) {
        switch (cat) {
            case "Coffee":
            case "Hot Coffee":
                if (itemsHotCoffee != null) itemsHotCoffee.addView(v);
                break;

            case "Cold Drinks":
            case "Iced Coffee":
                if (itemsIcedCoffee != null) itemsIcedCoffee.addView(v);
                break;

            case "Tea":
                if (itemsTea != null) itemsTea.addView(v);
                break;

            case "Frappuccino":
                if (itemsFrappuccino != null) itemsFrappuccino.addView(v);
                break;

            case "Pastries":
                if (itemsPastries != null) itemsPastries.addView(v);
                break;

            default:
                // fallback
                if (itemsFrappuccino != null) itemsFrappuccino.addView(v);
                break;
        }
    }

    /**
     * Better-looking item row than a transparent MaterialButton
     * (still easy & clickable)
     */
    private View createItemRow(String name, int cost) {
        View v = getLayoutInflater().inflate(R.layout.item_reward_row, null, false);

        MaterialCardView card = v.findViewById(R.id.rewardCard);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        TextView tvSubtitle = v.findViewById(R.id.tvSubtitle);
        Chip chipPoints = v.findViewById(R.id.chipPoints);
        ImageView ivSelected = v.findViewById(R.id.ivSelected);

        tvTitle.setText(name);
        tvSubtitle.setText("Tap to select");
        chipPoints.setText(cost + " pts");

        // store data on the view (useful for selection refresh)
        v.setTag(R.id.tvTitle, name);

        // apply selected UI if needed
        boolean isSelected = (selectedItemName != null && selectedItemName.equals(name));
        applyRewardRowSelectedState(card, ivSelected, isSelected);

        card.setOnClickListener(view -> {
            selectItem(name, cost);
            refreshRewardSelectionUI(); // update list visuals
        });

        return v;
    }

    private void applyRewardRowSelectedState(MaterialCardView card, ImageView ivSelected, boolean selected) {
        if (selected) {
            card.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
            card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(
                    card, com.google.android.material.R.attr.colorOnPrimary
            ));
            ivSelected.setVisibility(View.VISIBLE);
        } else {
            card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
            card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(
                    card, com.google.android.material.R.attr.colorOutline
            ));
            ivSelected.setVisibility(View.GONE);
        }
    }


    // -------------------------
    // SELECT ITEM + BOTTOM BAR
    // -------------------------
    private void selectItem(String name, int cost) {
        selectedItemName = name;
        selectedItemCost = cost;

        tvSelectedItem.setText(name);

        updateProgressUI();
        checkPointsAndWarn();
        updateRedeemButtonState();

        refreshRewardSelectionUI();
    }

    private void refreshRewardSelectionUI() {
        refreshContainerSelection(itemsHotCoffee);
        refreshContainerSelection(itemsIcedCoffee);
        refreshContainerSelection(itemsTea);
        refreshContainerSelection(itemsFrappuccino);
        refreshContainerSelection(itemsPastries);
    }

    private void refreshContainerSelection(LinearLayout container) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            MaterialCardView card = row.findViewById(R.id.rewardCard);
            ImageView ivSelected = row.findViewById(R.id.ivSelected);
            TextView title = row.findViewById(R.id.tvTitle);

            if (card == null || ivSelected == null || title == null) continue;

            boolean isSelected = (selectedItemName != null && selectedItemName.equals(title.getText().toString()));
            applyRewardRowSelectedState(card, ivSelected, isSelected);
        }
    }


    private void updateProgressUI() {
        if (selectedUserUid == null || selectedItemCost <= 0) {
            progressToReward.setProgress(0);
            tvProgressHint.setText("Select a reward to see required points.");
            return;
        }

        int progress = (int) Math.min(100, (selectedUserPoints * 100f) / selectedItemCost);
        progressToReward.setProgress(progress);

        if (selectedUserPoints >= selectedItemCost) {
            tvProgressHint.setText("Ready to redeem 🎉");
        } else {
            int missing = selectedItemCost - selectedUserPoints;
            tvProgressHint.setText("Needs " + missing + " more pts");
        }
    }

    private void checkPointsAndWarn() {
        if (selectedItemCost <= 0 || selectedItemName == null) {
            tvRequiredPoints.setText("-- pts required");
            tvRequiredPoints.setTextColor(Color.GRAY);
            return;
        }

        if (selectedUserUid == null) {
            tvRequiredPoints.setText("Select a customer first");
            tvRequiredPoints.setTextColor(Color.GRAY);
            return;
        }

        if (selectedUserPoints < selectedItemCost) {
            int missing = selectedItemCost - selectedUserPoints;
            tvRequiredPoints.setText("Missing " + missing + " pts");
            tvRequiredPoints.setTextColor(Color.parseColor("#EF4444"));
        } else {
            tvRequiredPoints.setText("Cost: " + selectedItemCost + " pts");
            tvRequiredPoints.setTextColor(Color.GRAY);
        }
    }

    private void updateRedeemButtonState() {
        boolean canRedeem =
                !isSearching
                        && selectedUserUid != null
                        && selectedItemName != null
                        && selectedItemCost > 0
                        && selectedUserPoints >= selectedItemCost;

        btnRedeem.setEnabled(canRedeem);
        btnRedeem.setAlpha(canRedeem ? 1f : 0.6f);
    }

    private void setupRedeemButton() {
        btnRedeem.setOnClickListener(v -> {
            if (selectedUserUid == null) { showToast("Select customer first"); return; }
            if (selectedItemName == null || selectedItemCost <= 0) { showToast("Select a reward first"); return; }
            if (selectedUserPoints < selectedItemCost) { showToast("Not enough points"); return; }
            createRedeemCode();
        });
    }

    private void createRedeemCode() {
        btnRedeem.setEnabled(false);
        btnRedeem.setAlpha(0.6f);
        btnRedeem.setText("Generating...");

        Map<String, Object> data = new HashMap<>();
        data.put("userUid", selectedUserUid);
        data.put("userDocId", selectedUserDocId);
        data.put("userName", selectedUserName);
        data.put("itemName", selectedItemName);
        data.put("costPoints", selectedItemCost);
        data.put("clientPointsBefore", selectedUserPoints);
        data.put("cashierId", cashierId);
        if (cashierName != null) data.put("cashierName", cashierName);
        if (shopId != null) data.put("shopId", shopId);
        data.put("status", "ACTIVE");
        data.put("type", "REDEEM");
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("redeem_codes")
                .add(data)
                .addOnSuccessListener(doc -> {
                    String codeId = doc.getId();
                    doc.update("codeId", codeId);

                    String payload = "REDEEM|" + codeId + "|" + selectedUserUid + "|" + selectedItemCost;

                    try {
                        Bitmap qr = generateQrCode(payload, 512);
                        showQrDialog(qr, selectedItemName, selectedItemCost);
                    } catch (WriterException e) {
                        showToast("QR generation failed");
                    }

                    btnRedeem.setText("Redeem");
                    updateRedeemButtonState();
                })
                .addOnFailureListener(e -> {
                    showToast("Failed: " + (e.getMessage() != null ? e.getMessage() : ""));
                    btnRedeem.setText("Redeem");
                    updateRedeemButtonState();
                });
    }

    // -------------------------
    // Cashier meta
    // -------------------------
    private void initCashierMeta() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            cashierId = user.getUid();
            cashierName = user.getEmail();
            db.collection("users").document(cashierId).get().addOnSuccessListener(s -> {
                if (s.exists() && s.getString("fullName") != null) cashierName = s.getString("fullName");
            });
        }
    }

    // -------------------------
    // Eligibility chip helpers (no custom colors required)
    // -------------------------
    private void setEligibilityNeutral() {
        eligibilityChip.setText("Select a reward");
        eligibilityChip.setChipBackgroundColorResource(android.R.color.transparent);
        eligibilityChip.setChipStrokeWidth(1f);
        eligibilityChip.setChipStrokeColorResource(android.R.color.darker_gray);
        eligibilityChip.setTextColor(ContextCompat.getColor(this, android.R.color.black));
    }

    @SuppressLint("ResourceAsColor")
    private void setEligibilitySuccess(String text) {
        eligibilityChip.setText(text);
        eligibilityChip.setChipStrokeWidth(0f);
        eligibilityChip.setChipBackgroundColorResource(R.color.green_800);
        eligibilityChip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    private void setEligibilityWarning(String text) {
        eligibilityChip.setText(text);
        eligibilityChip.setChipStrokeWidth(0f);
        eligibilityChip.setChipBackgroundColorResource(android.R.color.holo_red_light);
        eligibilityChip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    private void setEligibilityInfo(String text) {
        eligibilityChip.setText(text);
        eligibilityChip.setChipStrokeWidth(0f);
        eligibilityChip.setChipBackgroundColorResource(android.R.color.darker_gray);
        eligibilityChip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    // -------------------------
    // QR
    // -------------------------
    private Bitmap generateQrCode(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    private void showQrDialog(Bitmap bitmap, String name, int cost) {
        View v = getLayoutInflater().inflate(R.layout.dialog_qr_redeem, null);
        ImageView iv = v.findViewById(R.id.ivQrCode);
        TextView tv = v.findViewById(R.id.tvRedeemInfo);

        if (iv != null) iv.setImageBitmap(bitmap);
        if (tv != null) tv.setText("Scan to confirm\n" + name + " (" + cost + " pts)");

        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
