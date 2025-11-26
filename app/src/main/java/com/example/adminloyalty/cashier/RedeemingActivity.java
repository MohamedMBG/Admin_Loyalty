package com.example.adminloyalty.cashier;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

    // -------- UI --------
    private EditText searchClientEt;
    private MaterialCardView clientCard;
    private TextView clientNameTv, clientIdTv, clientPointsTv;
    private TextView tvSelectedItem, tvRequiredPoints;
    private MaterialButton btnRedeem;

    private LinearLayout headerHotCoffee, itemsHotCoffee;
    private LinearLayout headerIcedCoffee, itemsIcedCoffee;
    private LinearLayout headerTea, itemsTea;
    private LinearLayout headerFrappuccino, itemsFrappuccino;
    private LinearLayout headerPastries, itemsPastries;

    private View dividerHotCoffee, dividerIcedCoffee, dividerTea, dividerFrappuccino, dividerPastries;

    // -------- Firestore --------
    private FirebaseFirestore db;

    // -------- State --------
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

    // region Init Views
    private void initViews() {
        searchClientEt = findViewById(R.id.searchClient);
        clientCard = findViewById(R.id.clientCard);
        clientNameTv = findViewById(R.id.clientName);
        clientIdTv = findViewById(R.id.clientId);
        clientPointsTv = findViewById(R.id.clientPoints);

        tvSelectedItem = findViewById(R.id.tvSelectedItem);
        tvRequiredPoints = findViewById(R.id.tvRequiredPoints);
        btnRedeem = findViewById(R.id.btnRedeem);

        headerHotCoffee = findViewById(R.id.headerHotCoffee);
        itemsHotCoffee = findViewById(R.id.itemsHotCoffee);
        dividerHotCoffee = findViewById(R.id.dividerHotCoffee);

        headerIcedCoffee = findViewById(R.id.headerIcedCoffee);
        itemsIcedCoffee = findViewById(R.id.itemsIcedCoffee);
        dividerIcedCoffee = findViewById(R.id.dividerIcedCoffee);

        headerTea = findViewById(R.id.headerTea);
        itemsTea = findViewById(R.id.itemsTea);
        dividerTea = findViewById(R.id.dividerTea);

        headerFrappuccino = findViewById(R.id.headerFrappuccino);
        itemsFrappuccino = findViewById(R.id.itemsFrappuccino);
        dividerFrappuccino = findViewById(R.id.dividerFrappuccino);

        headerPastries = findViewById(R.id.headerPastries);
        itemsPastries = findViewById(R.id.itemsPastries);
        dividerPastries = findViewById(R.id.dividerPastries);
    }
    // endregion


    // region Category expand/collapse
    private void setupCategoryToggles() {
        setupToggle(headerHotCoffee, itemsHotCoffee, dividerHotCoffee);
        setupToggle(headerIcedCoffee, itemsIcedCoffee, dividerIcedCoffee);
        setupToggle(headerTea, itemsTea, dividerTea);
        setupToggle(headerFrappuccino, itemsFrappuccino, dividerFrappuccino);
        setupToggle(headerPastries, itemsPastries, dividerPastries);
    }

    private void setupToggle(LinearLayout header, LinearLayout content, View divider) {
        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
                if (divider != null) divider.setVisibility(View.GONE);
            } else {
                content.setVisibility(View.VISIBLE);
                if (divider != null) divider.setVisibility(View.VISIBLE);
            }
        });
    }

    // endregion


    // region Items selection
    private void setupItemButtons() {
        // HOT COFFEE
        setupItemButton(R.id.btnEspresso, "Espresso", 30);
        setupItemButton(R.id.btnAmericano, "Americano", 35);
        setupItemButton(R.id.btnCappuccino, "Cappuccino", 40);
        setupItemButton(R.id.btnLatte, "Latte", 45);
        setupItemButton(R.id.btnMocha, "Mocha", 45);
        setupItemButton(R.id.btnMacchiato, "Macchiato", 50);

        // ICED COFFEE
        setupItemButton(R.id.btnIcedAmericano, "Iced Americano", 40);
        setupItemButton(R.id.btnIcedLatte, "Iced Latte", 50);
        setupItemButton(R.id.btnIcedMocha, "Iced Mocha", 55);
        setupItemButton(R.id.btnColdBrew, "Cold Brew", 60);

        // TEA
        setupItemButton(R.id.btnGreenTea, "Green Tea", 25);
        setupItemButton(R.id.btnBlackTea, "Black Tea", 25);
        setupItemButton(R.id.btnChamomile, "Chamomile Tea", 30);
        setupItemButton(R.id.btnMatchaLatte, "Matcha Latte", 45);
        setupItemButton(R.id.btnChaiLatte, "Chai Latte", 40);

        // FRAPPUCCINO
        setupItemButton(R.id.btnCoffeeFrappuccino, "Coffee Frappuccino", 60);
        setupItemButton(R.id.btnCaramelFrappuccino, "Caramel Frappuccino", 65);
        setupItemButton(R.id.btnMochaFrappuccino, "Mocha Frappuccino", 65);
        setupItemButton(R.id.btnVanillaFrappuccino, "Vanilla Frappuccino", 60);

        // PASTRIES
        setupItemButton(R.id.btnCroissant, "Butter Croissant", 25);
        setupItemButton(R.id.btnChocolateCroissant, "Chocolate Croissant", 30);
        setupItemButton(R.id.btnMuffin, "Muffin", 30);
        setupItemButton(R.id.btnCakeSlice, "Cake Slice", 35);
        setupItemButton(R.id.btnCookie, "Cookie", 20);
    }

    private void setupItemButton(int buttonId, String itemName, int costPoints) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> {
            selectedItemName = itemName;
            selectedItemCost = costPoints;

            tvSelectedItem.setText(itemName);
            tvRequiredPoints.setText("Required points: " + costPoints);
        });
    }

    // endregion


    // region Search user (by email, uid, fullName)

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
        // If contains '@' → search by email
        if (query.contains("@")) {
            db.collection("users")
                    .whereEqualTo("email", query)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(this::handleUserResult)
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            return;
        }

        // First try UID
        db.collection("users")
                .whereEqualTo("uid", query)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        handleUserResult(snapshot);
                    } else {
                        // fallback: search by fullName
                        searchUserByName(query);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void searchUserByName(String name) {
        db.collection("users")
                .whereEqualTo("fullName", name)
                .limit(1)
                .get()
                .addOnSuccessListener(this::handleUserResult)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void handleUserResult(QuerySnapshot querySnapshot) {
        if (querySnapshot.isEmpty()) {
            Toast.makeText(this, "No user found", Toast.LENGTH_SHORT).show();
            clientCard.setVisibility(View.GONE);
            selectedUserUid = null;
            selectedUserDocId = null;
            selectedUserName = null;
            selectedUserPoints = 0;
            return;
        }

        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

        selectedUserDocId = doc.getId();
        selectedUserUid = doc.getString("uid");
        if (selectedUserUid == null) {
            selectedUserUid = selectedUserDocId; // fallback
        }

        selectedUserName = doc.getString("fullName");
        Long pts = doc.getLong("points");
        selectedUserPoints = pts != null ? pts.intValue() : 0;

        clientNameTv.setText(selectedUserName != null ? selectedUserName : "--");
        clientIdTv.setText("UID: " + selectedUserUid);
        clientPointsTv.setText(String.valueOf(selectedUserPoints));

        clientCard.setVisibility(View.VISIBLE);
    }

    // endregion


    // region Redeem QR creation

    private void setupRedeemButton() {
        btnRedeem.setOnClickListener(v -> {
            if (selectedUserUid == null) {
                Toast.makeText(this, "Select a user first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedItemName == null) {
                Toast.makeText(this, "Select an item to redeem", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedUserPoints < selectedItemCost) {
                Toast.makeText(this, "Not enough points", Toast.LENGTH_SHORT).show();
                return;
            }

            createRedeemCode();
        });
    }

    private void createRedeemCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("userUid", selectedUserUid);
        data.put("userDocId", selectedUserDocId);
        data.put("userName", selectedUserName);
        data.put("itemName", selectedItemName);
        data.put("costPoints", selectedItemCost);
        data.put("used", false);
        data.put("type", "redeem");
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("redeem_codes")
                .add(data)
                .addOnSuccessListener(docRef -> {
                    String codeId = docRef.getId();
                    String payload = "REDEEM|" + codeId;  // what the scanner will read

                    try {
                        Bitmap qr = generateQrCode(payload, 512);
                        showQrDialog(qr, selectedItemName, selectedItemCost);
                    } catch (WriterException e) {
                        Toast.makeText(this, "QR error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error creating redeem code: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Generate a square QR bitmap
    private Bitmap generateQrCode(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bmp;
    }

    private void showQrDialog(Bitmap bitmap, String itemName, int costPoints) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_redeem, null);
        ImageView qrImage = dialogView.findViewById(R.id.ivQrCode);
        TextView tvInfo = dialogView.findViewById(R.id.tvRedeemInfo);

        qrImage.setImageBitmap(bitmap);
        tvInfo.setText("Show this QR to be scanned by customer app.\n"
                + "Item: " + itemName + "\n"
                + "Cost: " + costPoints + " pts");

        new AlertDialog.Builder(this)
                .setTitle("Redemption QR")
                .setView(dialogView)
                .setPositiveButton("Close", (d, which) -> d.dismiss())
                .show();
    }
    // endregion
}