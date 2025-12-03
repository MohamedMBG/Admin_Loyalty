package com.example.adminloyalty.cashier;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

public class RedeemingActivity extends AppCompatActivity {

    // -------- UI Views --------
    private EditText searchClientEt;
    private MaterialCardView clientCard;
    private TextView clientNameTv, clientIdTv, clientPointsTv;
    private TextView tvSelectedItem, tvRequiredPoints;
    private MaterialButton btnRedeem;

    // Category Headers (Clickable)
    private LinearLayout headerHotCoffee, headerIcedCoffee, headerTea, headerFrappuccino, headerPastries;

    // Category Items Containers (Expandable)
    private LinearLayout itemsHotCoffee, itemsIcedCoffee, itemsTea, itemsFrappuccino, itemsPastries;

    // -------- Firestore --------
    private FirebaseFirestore db;

    // -------- State Variables --------
    private String selectedUserUid = null;
    private String selectedUserDocId = null;
    private String selectedUserName = null;
    private int selectedUserPoints = 0;

    private String selectedItemName = null;
    private int selectedItemCost = 0;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redeeming);

        db = FirebaseFirestore.getInstance();

        initViews();
        setupCategoryToggles();
        setupItemButtons();
        setupSearch();
        setupRedeemButton();
    }

    // region 1. Initialization
    private void initViews() {
        // Search & Client Profile
        searchClientEt = findViewById(R.id.searchClient);
        clientCard = findViewById(R.id.clientCard);
        clientNameTv = findViewById(R.id.clientName);
        clientIdTv = findViewById(R.id.clientId);
        clientPointsTv = findViewById(R.id.clientPoints);

        // Bottom Bar
        tvSelectedItem = findViewById(R.id.tvSelectedItem);
        tvRequiredPoints = findViewById(R.id.tvRequiredPoints);
        btnRedeem = findViewById(R.id.btnRedeem);

        // Category: Hot Coffee
        headerHotCoffee = findViewById(R.id.headerHotCoffee);
        itemsHotCoffee = findViewById(R.id.itemsHotCoffee);

        // Category: Iced Coffee
        headerIcedCoffee = findViewById(R.id.headerIcedCoffee);
        itemsIcedCoffee = findViewById(R.id.itemsIcedCoffee);

        // Category: Tea
        headerTea = findViewById(R.id.headerTea);
        itemsTea = findViewById(R.id.itemsTea);

        // Category: Frappuccino
        headerFrappuccino = findViewById(R.id.headerFrappuccino);
        itemsFrappuccino = findViewById(R.id.itemsFrappuccino);

        // Category: Pastries
        headerPastries = findViewById(R.id.headerPastries);
        itemsPastries = findViewById(R.id.itemsPastries);
    }
    // endregion


    // region 2. Categories Logic
    private void setupCategoryToggles() {
        setupToggle(headerHotCoffee, itemsHotCoffee);
        setupToggle(headerIcedCoffee, itemsIcedCoffee);
        setupToggle(headerTea, itemsTea);
        setupToggle(headerFrappuccino, itemsFrappuccino);
        setupToggle(headerPastries, itemsPastries);
    }

    private void setupToggle(LinearLayout header, LinearLayout content) {
        if (header == null || content == null) return;

        header.setOnClickListener(v -> {
            boolean isVisible = content.getVisibility() == View.VISIBLE;
            content.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        });
    }
    // endregion


    // region 3. Items Selection Logic
    private void setupItemButtons() {
        // --- Hot Coffee (6 Items) ---
        setupItemRow(R.id.btnEspresso, "Espresso", 50);
        setupItemRow(R.id.btnCappuccino, "Cappuccino", 200);
        setupItemRow(R.id.btnLatte, "Latte", 200);
        setupItemRow(R.id.btnAmericano, "Americano", 180);
        setupItemRow(R.id.btnMocha, "Mocha", 220);
        setupItemRow(R.id.btnMacchiato, "Macchiato", 190);

        // --- Iced Coffee (6 Items) ---
        setupItemRow(R.id.btnIcedAmericano, "Iced Americano", 180);
        setupItemRow(R.id.btnIcedLatte, "Iced Latte", 220);
        setupItemRow(R.id.btnIcedMocha, "Iced Mocha", 240);
        setupItemRow(R.id.btnColdBrew, "Cold Brew", 250);
        setupItemRow(R.id.btnIcedCaramelMacchiato, "Iced Caramel Macchiato", 260);
        setupItemRow(R.id.btnIcedShakenEspresso, "Iced Shaken Espresso", 230);

        // --- Tea (6 Items) ---
        setupItemRow(R.id.btnGreenTea, "Green Tea", 150);
        setupItemRow(R.id.btnBlackTea, "Black Tea", 150);
        setupItemRow(R.id.btnChamomile, "Chamomile Tea", 160);
        setupItemRow(R.id.btnMatchaLatte, "Matcha Latte", 250);
        setupItemRow(R.id.btnChaiLatte, "Chai Latte", 240);
        setupItemRow(R.id.btnEarlGrey, "Earl Grey", 150);

        // --- Frappuccino (6 Items) ---
        setupItemRow(R.id.btnCoffeeFrappuccino, "Coffee Frappuccino", 280);
        setupItemRow(R.id.btnCaramelFrappuccino, "Caramel Frappuccino", 300);
        setupItemRow(R.id.btnMochaFrappuccino, "Mocha Frappuccino", 300);
        setupItemRow(R.id.btnVanillaFrappuccino, "Vanilla Frappuccino", 280);
        setupItemRow(R.id.btnJavaChipFrappuccino, "Java Chip Frappuccino", 320);
        setupItemRow(R.id.btnStrawberryFrappuccino, "Strawberry Frappuccino", 320);

        // --- Pastries (6 Items) ---
        setupItemRow(R.id.btnCroissant, "Butter Croissant", 120);
        setupItemRow(R.id.btnChocolateCroissant, "Choco Croissant", 140);
        setupItemRow(R.id.btnMuffin, "Blueberry Muffin", 150);
        setupItemRow(R.id.btnCakeSlice, "Cake Slice", 180);
        setupItemRow(R.id.btnCookie, "Choco Chip Cookie", 100);
        setupItemRow(R.id.btnBrownie, "Fudge Brownie", 160);
    }

    /**
     * Helper to bind click listeners to rows.
     * Checks for null in case XML is not yet updated with all IDs.
     */
    private void setupItemRow(int viewId, String itemName, int costPoints) {
        View row = findViewById(viewId);
        if (row == null) return; // ID missing in XML, skip safely

        row.setOnClickListener(v -> {
            // 1. Update State
            selectedItemName = itemName;
            selectedItemCost = costPoints;

            // 2. Update UI Feedback
            tvSelectedItem.setText(itemName);
            tvRequiredPoints.setText("Cost: " + costPoints + " pts");

            // 3. Verify Points Immediately (Visual check)
            if (selectedUserUid != null) {
                checkPointsAndWarn();
            }
        });
    }

    private void checkPointsAndWarn() {
        if (selectedItemCost <= 0) {
            tvRequiredPoints.setTextColor(
                    ContextCompat.getColor(this, android.R.color.darker_gray)
            );
            tvRequiredPoints.setText("Select an item");
            return;
        }

        if (selectedUserPoints < selectedItemCost) {
            tvRequiredPoints.setTextColor(Color.RED);
            tvRequiredPoints.setText(
                    "Insufficient Points (Client: " + selectedUserPoints +
                            " / Cost: " + selectedItemCost + ")"
            );
        } else {
            tvRequiredPoints.setTextColor(
                    ContextCompat.getColor(this, android.R.color.darker_gray)
            );
            tvRequiredPoints.setText("Cost: " + selectedItemCost + " pts");
        }
    }
    // endregion


    // region 4. Client Search Logic
    private void setupSearch() {
        searchClientEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchClientEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {

                String query = searchClientEt.getText().toString().trim();
                if (!TextUtils.isEmpty(query)) {
                    searchUser(query);
                }
                return true;
            }
            return false;
        });
    }

    private void searchUser(String query) {
        // Strategy: Try Email -> then UID -> then Name
        if (query.contains("@")) {
            db.collection("users")
                    .whereEqualTo("email", query)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(this::handleUserResult)
                    .addOnFailureListener(e -> showToast("Error: " + e.getMessage()));
            return;
        }

        // Try UID
        db.collection("users")
                .whereEqualTo("uid", query)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        handleUserResult(snapshot);
                    } else {
                        // Fallback: Search by Name
                        searchUserByName(query);
                    }
                })
                .addOnFailureListener(e -> showToast("Search Error: " + e.getMessage()));
    }

    private void searchUserByName(String name) {
        db.collection("users")
                .whereEqualTo("fullName", name)
                .limit(1)
                .get()
                .addOnSuccessListener(this::handleUserResult)
                .addOnFailureListener(e -> showToast("Search Error: " + e.getMessage()));
    }

    private void handleUserResult(QuerySnapshot querySnapshot) {
        if (querySnapshot.isEmpty()) {
            showToast("No user found.");
            clientCard.setVisibility(View.GONE);
            selectedUserUid = null;
            selectedUserDocId = null;
            selectedUserName = null;
            selectedUserPoints = 0;
            checkPointsAndWarn();
            return;
        }

        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

        // Update State
        selectedUserDocId = doc.getId();
        selectedUserUid = doc.getString("uid");
        if (selectedUserUid == null) selectedUserUid = selectedUserDocId;

        selectedUserName = doc.getString("fullName");
        Long pts = doc.getLong("points");
        selectedUserPoints = pts != null ? pts.intValue() : 0;

        // Update UI
        clientNameTv.setText(selectedUserName != null ? selectedUserName : "Unknown User");
        clientIdTv.setText("ID: " + selectedUserUid);
        clientPointsTv.setText(String.format("%,d", selectedUserPoints));

        clientCard.setVisibility(View.VISIBLE);

        // Re-check points against currently selected item (if any)
        if (selectedItemName != null) {
            checkPointsAndWarn();
        }
    }
    // endregion


    // region 5. Redemption Logic
    private void setupRedeemButton() {
        btnRedeem.setOnClickListener(v -> {
            // Validation Steps
            if (selectedUserUid == null || selectedUserDocId == null) {
                showToast("Please search and select a client first.");
                return;
            }
            if (selectedItemName == null || selectedItemCost <= 0) {
                showToast("Please select an item to redeem.");
                return;
            }
            if (selectedUserPoints < selectedItemCost) {
                showAlert("Insufficient Points",
                        "Client has " + selectedUserPoints + " pts, but item costs " + selectedItemCost + " pts.");
                return;
            }

            // Proceed
            createRedeemCode();
        });
    }

    private void createRedeemCode() {
        // Extra safety: disable button to avoid double tap
        btnRedeem.setEnabled(false);
        btnRedeem.setText("Generating...");

        // Create a transaction record in "redeem_codes" collection
        Map<String, Object> data = new HashMap<>();
        data.put("userUid", selectedUserUid);
        data.put("userDocId", selectedUserDocId);
        data.put("userName", selectedUserName);
        data.put("itemName", selectedItemName);
        data.put("costPoints", selectedItemCost);
        data.put("clientPointsBefore", selectedUserPoints);

        // IMPORTANT: keep these strings consistent with what user app expects
        data.put("status", "ACTIVE");   // or "pending" but then scanner must check for that
        data.put("type", "REDEEM");     // upper-case to be explicit
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("redeem_codes")
                .add(data)
                .addOnSuccessListener(docRef -> {
                    String codeId = docRef.getId();

                    // Optionally store the codeId inside the document for easy lookup on the client
                    docRef.update("codeId", codeId);

                    // ---------- QR PAYLOAD FORMAT ----------
                    // REDEEM | codeId | userUid | costPoints
                    // Example: REDEEM|abc123|UID_456|200
                    // The USER app scanner must parse this and hit /redeem_codes/{codeId}
                    String payload = "REDEEM|" + codeId + "|" + selectedUserUid + "|" + selectedItemCost;

                    try {
                        Bitmap qr = generateQrCode(payload, 512);
                        showQrDialog(qr, selectedItemName, selectedItemCost);
                    } catch (WriterException e) {
                        e.printStackTrace();
                        showToast("QR Generation failed: " + e.getMessage());
                    }

                    btnRedeem.setEnabled(true);
                    btnRedeem.setText("Redeem");
                })
                .addOnFailureListener(e -> {
                    showToast("Database error: " + e.getMessage());
                    btnRedeem.setEnabled(true);
                    btnRedeem.setText("Redeem");
                });
    }
    // endregion


    // region 6. QR Helper & Dialog
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

    private void showQrDialog(Bitmap bitmap, String itemName, int costPoints) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_redeem, null);
        ImageView qrImage = dialogView.findViewById(R.id.ivQrCode);
        TextView tvInfo = dialogView.findViewById(R.id.tvRedeemInfo);

        if (qrImage != null) qrImage.setImageBitmap(bitmap);
        if (tvInfo != null) {
            tvInfo.setText("Success! Points reserved.\nScan to confirm deduction.\n\n"
                    + itemName + " (" + costPoints + " pts)");
        }

        new AlertDialog.Builder(this)
                .setTitle("Redemption Pending")
                .setView(dialogView)
                .setPositiveButton("Done", (d, which) -> {
                    d.dismiss();
                    // Optional: reset UI for next client
                    // resetUI();
                })
                .show();
    }
    // endregion


    // region 7. Helpers
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showAlert(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
    // endregion
}
