package com.example.adminloyalty.cashier;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private MaterialCardView clientCard;
    private TextView clientNameTv, clientIdTv, clientPointsTv;
    private TextView tvSelectedItem, tvRequiredPoints;
    private MaterialButton btnRedeem;
    private LinearLayout layoutPromoContainer;

    // Menu Containers
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

    // --- System Verification Data ---
    private String selectedUserGender = null;
    private String selectedUserBirthday = null;
    private String selectedUserAddress = null;
    private Timestamp selectedUserLastVisit = null;

    // Transaction Data
    private String selectedItemName = null;
    private int selectedItemCost = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redeeming);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        setupCategoryToggles();
        loadRewardsFromCatalog();
        setupSearch();
        setupRedeemButton();
        initCashierMeta();
    }

    private void initViews() {
        searchClientEt = findViewById(R.id.searchClient);
        clientCard = findViewById(R.id.clientCard);
        clientNameTv = findViewById(R.id.clientName);
        clientIdTv = findViewById(R.id.clientId);
        clientPointsTv = findViewById(R.id.clientPoints);
        layoutPromoContainer = findViewById(R.id.layoutPromoContainer);

        tvSelectedItem = findViewById(R.id.tvSelectedItem);
        tvRequiredPoints = findViewById(R.id.tvRequiredPoints);
        btnRedeem = findViewById(R.id.btnRedeem);

        headerHotCoffee = findViewById(R.id.headerHotCoffee); itemsHotCoffee = findViewById(R.id.itemsHotCoffee);
        headerIcedCoffee = findViewById(R.id.headerIcedCoffee); itemsIcedCoffee = findViewById(R.id.itemsIcedCoffee);
        headerTea = findViewById(R.id.headerTea); itemsTea = findViewById(R.id.itemsTea);
        headerFrappuccino = findViewById(R.id.headerFrappuccino); itemsFrappuccino = findViewById(R.id.itemsFrappuccino);
        headerPastries = findViewById(R.id.headerPastries); itemsPastries = findViewById(R.id.itemsPastries);
    }

    // --- SEARCH & SYSTEM VERIFICATION START ---
    private void setupSearch() {
        searchClientEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchClientEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String query = searchClientEt.getText().toString().trim();
                if (!TextUtils.isEmpty(query)) searchUser(query);
                return true;
            }
            return false;
        });
    }

    private void searchUser(String query) {
        String cleanQuery = query.trim();
        // 1. Email Search
        if (cleanQuery.contains("@")) {
            db.collection("users").whereEqualTo("email", cleanQuery).limit(1)
                    .get().addOnSuccessListener(this::handleUserResult);
            return;
        }
        // 2. Smart Phone Search (+212 format)
        String formattedPhone = cleanQuery;
        String temp = cleanQuery.replace(" ", "");
        if (temp.matches("^0[567]\\d{8}$")) formattedPhone = "+212 " + temp.substring(1);
        else if (temp.matches("^[567]\\d{8}$")) formattedPhone = "+212 " + temp;

        searchUserByPhone(formattedPhone, cleanQuery);
    }

    private void searchUserByPhone(String phoneQuery, String originalQuery) {
        db.collection("users").whereEqualTo("phone", phoneQuery).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) handleUserResult(snap);
                    else searchUserByUid(originalQuery);
                });
    }

    private void searchUserByUid(String query) {
        db.collection("users").whereEqualTo("uid", query).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) handleUserResult(snap);
                    else searchUserByName(query);
                });
    }

    private void searchUserByName(String name) {
        db.collection("users").whereEqualTo("fullName", name).limit(1).get()
                .addOnSuccessListener(this::handleUserResult)
                .addOnFailureListener(e -> showToast("User not found"));
    }

    private void handleUserResult(QuerySnapshot querySnapshot) {
        if (querySnapshot.isEmpty()) {
            showToast("No user found.");
            clientCard.setVisibility(View.GONE);
            return;
        }

        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
        selectedUserDocId = doc.getId();
        selectedUserUid = doc.getString("uid");
        selectedUserName = doc.getString("fullName");
        Long pts = doc.getLong("points");
        selectedUserPoints = pts != null ? pts.intValue() : 0;

        // --- FETCH DATA FOR SYSTEM VERIFICATION ---
        selectedUserGender = doc.getString("gender");
        selectedUserBirthday = doc.getString("birthday"); // "YYYY-MM-DD"
        selectedUserAddress = doc.getString("address");
        selectedUserLastVisit = doc.getTimestamp("lastVisitTimestamp");

        // Update UI
        clientNameTv.setText(selectedUserName != null ? selectedUserName : "Unknown User");
        clientIdTv.setText("ID: " + selectedUserUid);
        clientPointsTv.setText(String.format("%,d", selectedUserPoints));
        clientCard.setVisibility(View.VISIBLE);

        // --- TRIGGER SYSTEM CHECK ---
        verifySystemPromotions();

        if (selectedItemName != null) checkPointsAndWarn();
    }

    // --- SYSTEM VERIFICATION ENGINE (UPDATED) ---
    private void verifySystemPromotions() {
        // Clear previous verifications
        layoutPromoContainer.removeAllViews();
        layoutPromoContainer.setVisibility(View.GONE);

        db.collection("promotions")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) return;

                    List<DocumentSnapshot> eligiblePromos = new ArrayList<>();

                    // 1. Gather ALL Eligible Offers
                    for (DocumentSnapshot promo : snapshots) {
                        if (runVerificationLogic(promo)) {
                            eligiblePromos.add(promo);
                        }
                    }

                    if (!eligiblePromos.isEmpty()) {
                        // 2. Sort by Priority (Descending: 10, 5, 1)
                        Collections.sort(eligiblePromos, (p1, p2) -> {
                            // Default to 0 if priority is missing
                            long prio1 = p1.getLong("priority") != null ? p1.getLong("priority") : 0;
                            long prio2 = p2.getLong("priority") != null ? p2.getLong("priority") : 0;
                            return Long.compare(prio2, prio1); // Descending
                        });

                        // 3. Display ONLY the Top Result (Most Profitable)
                        TextView header = new TextView(this);
                        header.setText("✅ SYSTEM VERIFIED OFFER:");
                        header.setTextSize(12);
                        header.setTextColor(Color.parseColor("#10B981"));
                        header.setTypeface(null, android.graphics.Typeface.BOLD);
                        header.setPadding(0, 0, 0, 8);
                        layoutPromoContainer.addView(header);

                        // Pick index 0 (Highest priority)
                        DocumentSnapshot topPromo = eligiblePromos.get(0);
                        addVerifiedBadge(topPromo.getString("title"));

                        layoutPromoContainer.setVisibility(View.VISIBLE);
                    }
                });
    }

    private boolean runVerificationLogic(DocumentSnapshot promo) {
        // 1. TIME CHECK (NEW)
        Timestamp start = promo.getTimestamp("startDate");
        Timestamp end = promo.getTimestamp("endDate");
        Timestamp now = Timestamp.now();

        // Check if start date is set and we are before it
        if (start != null && now.compareTo(start) < 0) return false;
        // Check if end date is set and we are after it
        if (end != null && now.compareTo(end) > 0) return false;

        // 2. CRITERIA CHECK
        String criteria = promo.getString("criteria");
        String ruleValue = promo.getString("value");

        if (criteria == null) return false;

        switch (criteria) {
            case "ALL":
                return true;

            case "GENDER":
                if (selectedUserGender == null || ruleValue == null) return false;
                return selectedUserGender.equalsIgnoreCase(ruleValue);

            case "AGE_UNDER":
                if (selectedUserBirthday == null || ruleValue == null) return false;
                int age = calculateAge(selectedUserBirthday);
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return age != -1 && age < limit;
                } catch (NumberFormatException e) { return false; }

            case "LOCATION_CONTAINS":
                if (selectedUserAddress == null || ruleValue == null) return false;
                return selectedUserAddress.toLowerCase().contains(ruleValue.toLowerCase());

            case "POINTS_UNDER":
                if (ruleValue == null) return false;
                try {
                    int limit = Integer.parseInt(ruleValue);
                    return selectedUserPoints < limit;
                } catch (NumberFormatException e) { return false; }

            case "NO_VISIT_DAYS":
                if (ruleValue == null) return false;
                try {
                    int daysThreshold = Integer.parseInt(ruleValue);
                    if (selectedUserLastVisit == null) return true; // Never visited = Eligible

                    long lastVisitMillis = selectedUserLastVisit.toDate().getTime();
                    long diffMillis = System.currentTimeMillis() - lastVisitMillis;
                    long diffDays = diffMillis / (1000 * 60 * 60 * 24);

                    return diffDays > daysThreshold;
                } catch (NumberFormatException e) { return false; }

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
        } catch (Exception e) { return -1; }
    }

    private void addVerifiedBadge(String title) {
        TextView badge = new TextView(this);
        // Use Checkmark to indicate system verification
        badge.setText("✓ " + title);
        badge.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        badge.setTextSize(14);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setPadding(8, 4, 0, 4);
        layoutPromoContainer.addView(badge);
    }
    // --- END SYSTEM VERIFICATION ---

    // --- STANDARD SETUP & HELPERS ---
    private void setupCategoryToggles() {
        setupToggle(headerHotCoffee, itemsHotCoffee);
        setupToggle(headerIcedCoffee, itemsIcedCoffee);
        setupToggle(headerTea, itemsTea);
        setupToggle(headerFrappuccino, itemsFrappuccino);
        setupToggle(headerPastries, itemsPastries);
    }
    private void setupToggle(LinearLayout header, LinearLayout content) {
        if (header != null && content != null)
            header.setOnClickListener(v -> content.setVisibility(content.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    }

    private void loadRewardsFromCatalog() {
        if (itemsHotCoffee != null) itemsHotCoffee.removeAllViews();
        // ... (clear others) ...
        db.collection("rewards_catalog").orderBy("name", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        String name = doc.getString("name");
                        String cat = doc.getString("category");
                        Long costL = doc.getLong("costPoints");
                        int cost = costL != null ? costL.intValue() : 0;
                        if (name != null && cat != null) {
                            View v = createItemView(name, cost);
                            addToContainer(cat, v);
                        }
                    }
                });
    }

    private void addToContainer(String cat, View v) {
        switch (cat) {
            case "Coffee": if (itemsHotCoffee != null) itemsHotCoffee.addView(v); break;
            case "Tea": if (itemsTea != null) itemsTea.addView(v); break;
            case "Pastries": if (itemsPastries != null) itemsPastries.addView(v); break;
            case "Cold Drinks": if (itemsIcedCoffee != null) itemsIcedCoffee.addView(v); break;
            case "Frappuccino": if (itemsFrappuccino != null) itemsFrappuccino.addView(v); break;
            default: if (itemsFrappuccino != null) itemsFrappuccino.addView(v); break;
        }
    }

    private View createItemView(String name, int cost) {
        MaterialButton btn = new MaterialButton(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 8, 0, 8);
        btn.setLayoutParams(p);
        btn.setText(name + " (" + cost + " pts)");
        btn.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        btn.setAllCaps(false);
        btn.setIconResource(android.R.drawable.btn_star_big_on);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        btn.setOnClickListener(v -> selectItem(name, cost));
        return btn;
    }

    private void selectItem(String name, int cost) {
        selectedItemName = name;
        selectedItemCost = cost;
        tvSelectedItem.setText(name);
        checkPointsAndWarn();
    }

    private void setupRedeemButton() {
        btnRedeem.setOnClickListener(v -> {
            if (selectedUserUid == null) { showToast("Select client first"); return; }
            if (selectedItemName == null) { showToast("Select item first"); return; }
            if (selectedUserPoints < selectedItemCost) { showToast("Insufficient points"); return; }
            createRedeemCode();
        });
    }

    private void createRedeemCode() {
        btnRedeem.setEnabled(false);
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
        if (shopId != null)     data.put("shopId", shopId);
        data.put("status", "ACTIVE");
        data.put("type", "REDEEM");
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("redeem_codes").add(data).addOnSuccessListener(doc -> {
            String codeId = doc.getId();
            doc.update("codeId", codeId);
            String payload = "REDEEM|" + codeId + "|" + selectedUserUid + "|" + selectedItemCost;
            try {
                Bitmap qr = generateQrCode(payload, 512);
                showQrDialog(qr, selectedItemName, selectedItemCost);
            } catch (WriterException e) { e.printStackTrace(); }
            btnRedeem.setEnabled(true);
            btnRedeem.setText("Redeem");
        });
    }

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

    private void checkPointsAndWarn() {
        if (selectedItemCost <= 0) {
            tvRequiredPoints.setText("Select an item");
            tvRequiredPoints.setTextColor(Color.GRAY);
        } else if (selectedUserPoints < selectedItemCost) {
            tvRequiredPoints.setText("Insufficient Points");
            tvRequiredPoints.setTextColor(Color.RED);
        } else {
            tvRequiredPoints.setText("Cost: " + selectedItemCost + " pts");
            tvRequiredPoints.setTextColor(Color.GRAY);
        }
    }

    private Bitmap generateQrCode(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
        return bmp;
    }

    private void showQrDialog(Bitmap bitmap, String name, int cost) {
        View v = getLayoutInflater().inflate(R.layout.dialog_qr_redeem, null);
        ImageView iv = v.findViewById(R.id.ivQrCode);
        TextView tv = v.findViewById(R.id.tvRedeemInfo);
        if (iv != null) iv.setImageBitmap(bitmap);
        if (tv != null) tv.setText("Scan to confirm\n" + name + " (" + cost + " pts)");
        new AlertDialog.Builder(this).setView(v).setPositiveButton("Done", null).show();
    }

    private void showToast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}