package com.example.adminloyalty.cashier;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.utils.SystemBars;
import com.example.adminloyalty.viewmodel.RedeemingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Cashier redemption screen.
 *
 * <p>The customer creates a pending redemption in their own app; the cashier's only job here is to
 * scan that QR code to mark it fulfilled ({@link RedeemingViewModel#completeRedeem(String)}). The
 * customer search is optional context — it confirms who is being served and shows their balance —
 * and never gates the scan.</p>
 */
@AndroidEntryPoint
public class RedeemingActivity extends AppCompatActivity {

    private EditText searchClientEt;
    private MaterialCardView clientCard;
    private android.widget.TextView clientNameTv;
    private Chip pointsChip;
    private MaterialButton btnRedeem;

    private RedeemingViewModel viewModel;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DEBOUNCE_MS = 350;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    viewModel.completeRedeem(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_redeeming);
        SystemBars.applyInsetPadding(findViewById(R.id.cashier_main));

        initViews();
        setupSearch();
        btnRedeem.setOnClickListener(v -> launchScanner());

        viewModel = new ViewModelProvider(this).get(RedeemingViewModel.class);
        observeViewModel();
    }

    private void initViews() {
        searchClientEt = findViewById(R.id.searchClient);
        clientCard = findViewById(R.id.clientCard);
        clientNameTv = findViewById(R.id.clientName);
        pointsChip = findViewById(R.id.pointsChip);
        btnRedeem = findViewById(R.id.btnRedeem);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

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
                debounceSearch(s.toString().trim());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void debounceSearch(String q) {
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        searchRunnable = () -> viewModel.searchUser(q);
        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void triggerSearchNow() {
        String q = searchClientEt.getText().toString().trim();
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        viewModel.searchUser(q);
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_redemption_code))
                .setBeepEnabled(false)
                .setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void observeViewModel() {
        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showToast(error);
                viewModel.clearStatus();
            }
        });

        viewModel.getSuccess().observe(this, payload -> {
            if (payload != null && !payload.isEmpty()) {
                showRedeemResultDialog(payload);
                viewModel.clearStatus();
            }
        });

        viewModel.getIsLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            btnRedeem.setEnabled(!busy);
            btnRedeem.setAlpha(busy ? 0.6f : 1f);
            btnRedeem.setText(busy ? R.string.completing : R.string.scan_reward_qr);
        });

        viewModel.getSelectedUser().observe(this, user -> {
            if (user != null) {
                clientCard.setVisibility(View.VISIBLE);
                clientNameTv.setText(user.displayName());
                pointsChip.setText(getString(R.string.points, user.points));
            } else {
                clientCard.setVisibility(View.GONE);
            }
        });
    }

    private void showRedeemResultDialog(String payload) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.redemption_complete)
                .setMessage(payload)
                .setPositiveButton(R.string.done, null)
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
