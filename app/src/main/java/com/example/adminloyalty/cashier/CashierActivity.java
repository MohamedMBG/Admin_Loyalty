package com.example.adminloyalty.cashier;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.adminloyalty.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class CashierActivity extends AppCompatActivity {

    // UI
    private View root;
    private MaterialCardView cardConfirm, cardQr;
    private MaterialTextView tvConfirmTitle, tvConfirmDetails, tvQrMeta, tvStatus;
    private Chip chipConfirmTimer, chipTimer;
    private ImageView imgQr;
    private ProgressBar progressIssuing;
    private TextInputEditText etReceipt, etAmount;
    private MaterialButton btnGenerate, btnConfirm, btnCancel, btnRefresh;

    // Firebase
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // State
    private ListenerRegistration voucherListener;
    private CountDownTimer confirmCdt, countdown;
    private String currentVoucherId;
    private DocumentReference currentVoucherRef;
    private int currentValidForSec = 120; // default TTL

    // -------------------- Activity --------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashier);
        bindViews();
        bindActions();
        resetUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeDocListener();
        cancelTimer(confirmCdt);
        cancelTimer(countdown);
    }

    // -------------------- Bindings --------------------
    @SuppressLint("WrongViewCast")
    private void bindViews() {
        root = findViewById(android.R.id.content);

        cardConfirm      = findViewById(R.id.card_confirm);
        cardQr           = findViewById(R.id.card_qr);
        tvConfirmTitle   = findViewById(R.id.tvConfirmTitle);
        tvConfirmDetails = findViewById(R.id.tv_confirm_details);
        tvQrMeta         = findViewById(R.id.tv_qr_meta);
        tvStatus         = findViewById(R.id.tv_status);
        chipConfirmTimer = findViewById(R.id.chip_confirm_timer);
        chipTimer        = findViewById(R.id.chip_timer);
        imgQr            = findViewById(R.id.img_qr);
        progressIssuing  = findViewById(R.id.progress_issuing);
        etAmount         = findViewById(R.id.et_amount);
        etReceipt        = findViewById(R.id.et_receipt);

        btnGenerate = findViewById(R.id.btn_generate);
        btnConfirm  = findViewById(R.id.btn_confirm);
        btnCancel   = findViewById(R.id.btn_cancel);
        btnRefresh  = findViewById(R.id.btn_refresh);
    }

    private void bindActions() {
        btnGenerate.setOnClickListener(v -> openConfirm());
        btnConfirm.setOnClickListener(v -> createVoucherAndShow());
        btnCancel.setOnClickListener(v -> cancelActive());
        btnRefresh.setOnClickListener(v -> openConfirm());
    }

    // -------------------- Flow --------------------
    /** Step 1: Show confirm card with a 10s auto-close timer */
    private void openConfirm() {
        String orderNo = text(etReceipt);
        String amtStr  = text(etAmount);

        if (orderNo.isEmpty()) { snack("Enter receipt number"); return; }
        if (amtStr.isEmpty())  { snack("Enter total amount"); return; }

        double amountMAD;
        try { amountMAD = Double.parseDouble(amtStr); }
        catch (NumberFormatException e) { snack("Invalid amount"); return; }

        tvConfirmTitle.setText("Confirm Sale");
        tvConfirmDetails.setText("#" + orderNo + " · " + amountMAD + " MAD");
        cardConfirm.setVisibility(View.VISIBLE);

        cancelTimer(confirmCdt);
        confirmCdt = new CountDownTimer(10_000, 1_000) {
            public void onTick(long left) { chipConfirmTimer.setText((left / 1000) + "s"); }
            public void onFinish() { cardConfirm.setVisibility(View.GONE); }
        }.start();
    }

    /** Step 2: Create Firestore earn_codes, draw QR (docId), start listener + countdown */
    private void createVoucherAndShow() {
        cancelTimer(confirmCdt);
        cardConfirm.setVisibility(View.GONE);

        String orderNo = text(etReceipt);
        String amtStr  = text(etAmount);

        if (orderNo.isEmpty() || amtStr.isEmpty()) { snack("Missing inputs"); return; }

        double amountMAD;
        try { amountMAD = Double.parseDouble(amtStr); }
        catch (NumberFormatException e) { snack("Invalid amount"); return; }

        // Decide points — simple heuristic; replace with your real rule if needed
        int points = (int) Math.max(1, Math.round(amountMAD / 5.0));

        progressIssuing.setVisibility(View.VISIBLE);
        tvStatus.setText("Creating…");

        db.collection("earn_codes")
                .whereEqualTo("orderNo", orderNo)
                .limit(1)
                .get()
                .addOnSuccessListener(snap ->{
                    if (!snap.isEmpty()) {
                        // Duplicate found -> STOP (no QR)
                        progressIssuing.setVisibility(View.GONE);
                        tvStatus.setText("Blocked");
                        snack("This receipt number already has a QR. No QR generated.");
                        return;
                    }


                    // 2) No duplicate -> build payload & create
                    tvStatus.setText("Creating voucher…");

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("orderNo", orderNo);
                    doc.put("amountMAD", amountMAD);
                    doc.put("points", points);
                    doc.put("status", "pending");
                    doc.put("createdAt", FieldValue.serverTimestamp());
                    doc.put("validForSec", currentValidForSec);
                    doc.put("redeemedAt", null);
                    doc.put("redeemedByUid", null);
                    doc.put("qrVersion", 1);
                    doc.put("nonce", randomNonce(10));

                    currentVoucherRef = db.collection("earn_codes").document(); // random ID
                    currentVoucherId  = currentVoucherRef.getId();

                    currentVoucherRef.set(doc).addOnSuccessListener(a -> {
                        // 3) Show QR only after successful create
                        renderQr(currentVoucherId); // QR contains the docId you just created
                        tvQrMeta.setText("Show to customer to scan");
                        progressIssuing.setVisibility(View.GONE);
                        tvStatus.setText("Active");

                        startDocListener(currentVoucherId);
                        startCountdown(currentValidForSec * 1000L);

                    }).addOnFailureListener(e -> {
                        progressIssuing.setVisibility(View.GONE);
                        tvStatus.setText("Error");
                        snack("Create failed: " + e.getMessage());
                    });

                })
                .addOnFailureListener(e -> {
                    progressIssuing.setVisibility(View.GONE);
                    tvStatus.setText("Error");
                    snack("Check failed: " + e.getMessage());
                });
    }

    // -------------------- Firestore listener --------------------
    private void startDocListener(String voucherId) {
        removeDocListener();
        DocumentReference ref = db.collection("earn_codes").document(voucherId);
        voucherListener = ref.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            String status = snap.getString("status");
            if (status == null) return;

            tvStatus.setText(status.toUpperCase(Locale.getDefault()));
            switch (status) {
                case "redeemed":
                    tvQrMeta.setText("Customer scanned successfully!");
                    imgQr.setAlpha(0.3f);
                    break;
                case "canceled":
                    tvQrMeta.setText("Canceled");
                    imgQr.setAlpha(0.3f);
                    break;
                default:
                    break;
            }
        });
    }

    private void removeDocListener() {
        if (voucherListener != null) { voucherListener.remove(); voucherListener = null; }
    }

    // -------------------- Countdown --------------------
    private void startCountdown(long durationMs) {
        cancelTimer(countdown);
        if (durationMs < 0) durationMs = 0;

        countdown = new CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long left) {
                long s = left / 1000, m = s / 60, r = s % 60;
                chipTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", m, r));
            }
            @Override public void onFinish() {
                tvStatus.setText("Expired");
                tvQrMeta.setText("QR expired. Generate a new one.");
                imgQr.setAlpha(0.3f);
            }
        }.start();
    }

    private void cancelTimer(CountDownTimer t) { if (t != null) t.cancel(); }

    // -------------------- QR --------------------
    /** Encode only the Firestore document ID inside the QR */
    private void renderQr(String value) {
        try {
            final int size = 720;
            BitMatrix matrix = new QRCodeWriter()
                    .encode(value, BarcodeFormat.QR_CODE, size, size);

            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            imgQr.setAlpha(1f);
            imgQr.setImageBitmap(bmp);
            tvQrMeta.setText("Show this QR to the customer to scan");
            tvStatus.setText("QR ready");

        } catch (WriterException e) {
            snack("QR generation failed: " + e.getMessage());
        }
    }

    // -------------------- Cancel / Reset --------------------
    private void cancelActive() {
        // Try to cancel the pending voucher on Firestore (if it exists)
        if (currentVoucherRef != null && currentVoucherId != null) {
            currentVoucherRef.get().addOnSuccessListener(snap -> {
                if (snap.exists()) {
                    String status = snap.getString("status");
                    if ("pending".equals(status)) {
                        currentVoucherRef.update("status", "canceled")
                                .addOnSuccessListener(a -> {})
                                .addOnFailureListener(e -> {});
                    }
                }
                // Regardless, clear UI
                teardownAndReset();
            }).addOnFailureListener(e -> teardownAndReset());
        } else {
            teardownAndReset();
        }
    }

    private void teardownAndReset() {
        removeDocListener();
        cancelTimer(countdown);
        resetUi();
    }

    private void resetUi() {
        imgQr.setImageBitmap(null);
        imgQr.setAlpha(1f);
        tvQrMeta.setText("Generate a QR to begin");
        tvStatus.setText("Ready");
        cardConfirm.setVisibility(View.GONE);
        progressIssuing.setVisibility(View.GONE);
        chipTimer.setText("--:--");
        currentVoucherId = null;
        currentVoucherRef = null;
    }

    // -------------------- Utils --------------------
    private String text(TextInputEditText et) {
        CharSequence cs = et.getText();
        return cs == null ? "" : cs.toString().trim();
    }

    private void snack(String msg) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    private static String randomNonce(int len) {
        final String alphabet = "0123456789abcdef";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }
}
