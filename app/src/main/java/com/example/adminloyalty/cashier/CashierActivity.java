package com.example.adminloyalty.cashier;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.utils.SystemBars;
import com.example.adminloyalty.viewmodel.CashierViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CashierActivity extends AppCompatActivity {

    private View root;
    private MaterialCardView cardConfirm, cardQr;
    private MaterialTextView tvConfirmTitle, tvConfirmDetails, tvQrMeta, tvStatus;
    private Chip chipConfirmTimer, chipTimer;
    private ImageView imgQr;
    private ProgressBar progressIssuing;
    private TextInputEditText etReceipt, etAmount;
    private MaterialButton btnGenerate, btnConfirm, btnCancel, btnRefresh, btn_redeeming;

    private CashierViewModel viewModel;
    private CountDownTimer confirmCdt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cashier);
        SystemBars.applyInsetPadding(findViewById(R.id.cashier_main));
        bindViews();
        bindActions();

        viewModel = new ViewModelProvider(this).get(CashierViewModel.class);
        observeViewModel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer(confirmCdt);
    }

    @SuppressLint("WrongViewCast")
    private void bindViews() {
        root = findViewById(android.R.id.content);

        btn_redeeming = findViewById(R.id.btn_redeeming);
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
        btnCancel.setOnClickListener(v -> viewModel.cancelActive());
        btnRefresh.setOnClickListener(v -> openConfirm());

        btn_redeeming.setOnClickListener(v -> {
            Intent intent = new Intent(CashierActivity.this, RedeemingActivity.class);
            startActivity(intent);
        });
    }

    private void observeViewModel() {
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                snack(msg);
                viewModel.clearError();
            }
        });

        viewModel.getStatusMessage().observe(this, msg -> {
            tvStatus.setText(msg);
        });

        viewModel.getQrMetaMessage().observe(this, msg -> {
            tvQrMeta.setText(msg);
        });

        viewModel.getIsIssuing().observe(this, isIssuing -> {
            progressIssuing.setVisibility(isIssuing != null && isIssuing ? View.VISIBLE : View.GONE);
        });

        viewModel.getVoucherId().observe(this, vid -> {
            if (vid != null) {
                renderQr(vid);
            } else {
                imgQr.setVisibility(View.INVISIBLE);
                imgQr.setImageBitmap(null);
                imgQr.setAlpha(1f);
            }
        });

        viewModel.getTimerText().observe(this, text -> {
            chipTimer.setText(text);
        });
    }

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

    private void createVoucherAndShow() {
        cancelTimer(confirmCdt);
        cardConfirm.setVisibility(View.GONE);

        String orderNo = text(etReceipt);
        String amtStr  = text(etAmount);

        viewModel.createVoucher(orderNo, amtStr);
    }

    private void cancelTimer(CountDownTimer t) { if (t != null) t.cancel(); }

    private void renderQr(String value) {
        try {
            final int size = 720;
            BitMatrix matrix = new QRCodeWriter()
                    .encode(value, BarcodeFormat.QR_CODE, size, size);

            // Fill a row-major int[] and blit it in one setPixels() call. Per-pixel setPixel()
            // over a 720x720 grid is ~518k individually locked writes on the UI thread; the
            // batched write is dramatically faster for the same output.
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            bmp.setPixels(pixels, 0, size, 0, 0, size, size);
            imgQr.setVisibility(View.VISIBLE);
            imgQr.setAlpha(1f);
            imgQr.setImageBitmap(bmp);

        } catch (WriterException e) {
            snack("QR generation failed: " + e.getMessage());
        }
    }

    private String text(TextInputEditText et) {
        CharSequence cs = et.getText();
        return cs == null ? "" : cs.toString().trim();
    }

    private void snack(String msg) {
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }
}
