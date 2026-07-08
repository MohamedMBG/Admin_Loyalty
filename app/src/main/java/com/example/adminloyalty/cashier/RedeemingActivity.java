package com.example.adminloyalty.cashier;

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

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.RewardItem;
import com.example.adminloyalty.viewmodel.RedeemingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RedeemingActivity extends AppCompatActivity {

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

    private LinearLayout headerHotCoffee, itemsHotCoffee;
    private LinearLayout headerIcedCoffee, itemsIcedCoffee;
    private LinearLayout headerTea, itemsTea;
    private LinearLayout headerFrappuccino, itemsFrappuccino;
    private LinearLayout headerPastries, itemsPastries;

    private RedeemingViewModel viewModel;

    private String selectedItemDocId = null;
    private String selectedItemName = null;
    private int selectedItemCost = 0;

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
        setContentView(R.layout.activity_redeeming);

        initViews();
        setupCategoryToggles();
        setupSearch();
        setupRedeemButton();

        viewModel = new ViewModelProvider(this).get(RedeemingViewModel.class);
        observeViewModel();

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
                debounceSearch(q);
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
            if (newVisibility == View.VISIBLE) content.post(content::requestFocus);
        });
    }

    private void setupRedeemButton() {
        btnRedeem.setText("Scan to Complete");
        btnRedeem.setOnClickListener(v -> launchScanner());
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the customer's redemption code")
                .setBeepEnabled(false)
                .setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void observeViewModel() {
        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                showToast(error);
                viewModel.clearStatus();
                updateRedeemButtonState();
            }
        });

        viewModel.getSuccess().observe(this, payload -> {
            if (payload != null && !payload.isEmpty()) {
                showRedeemResultDialog(payload);
                viewModel.clearStatus();
            }
        });

        viewModel.getIsLoading().observe(this, loading -> {
            if (loading != null) {
                btnRedeem.setEnabled(!loading);
                btnRedeem.setAlpha(loading ? 0.6f : 1f);
                btnRedeem.setText(loading ? "Completing..." : "Scan to Complete");
            }
        });

        viewModel.getRewardsList().observe(this, list -> {
            if (list != null) {
                clearCategoryContainers();
                for (RewardItem item : list) {
                    View itemView = createItemRow(item.getId(), item.getName(), item.getCostPoints());
                    addToContainer(item.getCategory(), itemView);
                }
            }
        });

        viewModel.getSelectedUser().observe(this, user -> {
            if (user != null) {
                emptyStateCard.setVisibility(View.GONE);
                clientCard.setVisibility(View.VISIBLE);

                clientNameTv.setText(user.displayName());

                int points = user.points;
                pointsChip.setText(points + " pts");

                updateProgressUI(points);
                checkPointsAndWarn(points);
                updateRedeemButtonState();
            } else {
                clearSelectedUserUI();
            }
        });

        viewModel.getIsSearching().observe(this, isSearching -> {
            updateRedeemButtonState(); // Update whenever searching status changes
        });
    }

    private void clearSelectedUserUI() {
        clientCard.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.VISIBLE);

        pointsChip.setText("0 pts");
        setEligibilityNeutral();

        promoCard.setVisibility(View.GONE);
        tvOfferText.setText("");

        progressToReward.setProgress(0);
        tvProgressHint.setText("Select a reward to see required points.");

        selectedItemName = null;
        selectedItemCost = 0;
        tvSelectedItem.setText("Select a reward");
        tvRequiredPoints.setText("-- pts required");
        tvRequiredPoints.setTextColor(Color.GRAY);

        updateRedeemButtonState();
    }

    private void clearCategoryContainers() {
        if (itemsHotCoffee != null) { itemsHotCoffee.removeAllViews(); itemsHotCoffee.setVisibility(View.GONE); }
        if (itemsIcedCoffee != null) { itemsIcedCoffee.removeAllViews(); itemsIcedCoffee.setVisibility(View.GONE); }
        if (itemsTea != null) { itemsTea.removeAllViews(); itemsTea.setVisibility(View.GONE); }
        if (itemsFrappuccino != null) { itemsFrappuccino.removeAllViews(); itemsFrappuccino.setVisibility(View.GONE); }
        if (itemsPastries != null) { itemsPastries.removeAllViews(); itemsPastries.setVisibility(View.GONE); }
    }

    private void addToContainer(String cat, View v) {
        if (cat == null) cat = "Frappuccino";

        switch (cat) {
            case "Coffee":
            case "Hot Coffee": if (itemsHotCoffee != null) itemsHotCoffee.addView(v); break;
            case "Cold Drinks":
            case "Iced Coffee": if (itemsIcedCoffee != null) itemsIcedCoffee.addView(v); break;
            case "Tea": if (itemsTea != null) itemsTea.addView(v); break;
            case "Frappuccino": if (itemsFrappuccino != null) itemsFrappuccino.addView(v); break;
            case "Pastries": if (itemsPastries != null) itemsPastries.addView(v); break;
            default: if (itemsFrappuccino != null) itemsFrappuccino.addView(v); break;
        }
    }

    private View createItemRow(String id, String name, int cost) {
        View v = getLayoutInflater().inflate(R.layout.item_reward_row, null, false);

        MaterialCardView card = v.findViewById(R.id.rewardCard);
        TextView tvTitle = v.findViewById(R.id.tvTitle);
        TextView tvSubtitle = v.findViewById(R.id.tvSubtitle);
        Chip chipPoints = v.findViewById(R.id.chipPoints);
        ImageView ivSelected = v.findViewById(R.id.ivSelected);

        tvTitle.setText(name);
        tvSubtitle.setText("Tap to select");
        chipPoints.setText(cost + " pts");

        v.setTag(R.id.tvTitle, name);
        v.setTag(R.id.rewardCard, id);

        boolean isSelected = (selectedItemDocId != null && selectedItemDocId.equals(id));
        applyRewardRowSelectedState(card, ivSelected, isSelected);

        card.setOnClickListener(view -> {
            selectItem(id, name, cost);
            refreshRewardSelectionUI();
        });

        return v;
    }

    private void selectItem(String id, String name, int cost) {
        selectedItemDocId = id;
        selectedItemName = name;
        selectedItemCost = cost;
        tvSelectedItem.setText(name);

        int points = 0;
        if (viewModel.getSelectedUser().getValue() != null) {
            points = viewModel.getSelectedUser().getValue().points;
        }

        updateProgressUI(points);
        checkPointsAndWarn(points);
        updateRedeemButtonState();
        refreshRewardSelectionUI();
    }

    private void applyRewardRowSelectedState(MaterialCardView card, ImageView ivSelected, boolean selected) {
        if (selected) {
            card.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
            card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimary));
            ivSelected.setVisibility(View.VISIBLE);
        } else {
            card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
            card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutline));
            ivSelected.setVisibility(View.GONE);
        }
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
            String rowId = (String) row.getTag(R.id.rewardCard);

            if (card == null || ivSelected == null) continue;
            boolean isSelected = (selectedItemDocId != null && selectedItemDocId.equals(rowId));
            applyRewardRowSelectedState(card, ivSelected, isSelected);
        }
    }

    private void updateProgressUI(int selectedUserPoints) {
        if (viewModel.getSelectedUser().getValue() == null || selectedItemCost <= 0) {
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

    private void checkPointsAndWarn(int selectedUserPoints) {
        if (selectedItemCost <= 0 || selectedItemName == null) {
            tvRequiredPoints.setText("-- pts required");
            tvRequiredPoints.setTextColor(Color.GRAY);
            return;
        }

        if (viewModel.getSelectedUser().getValue() == null) {
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

    // Completion now happens by scanning the customer's pending redeem code, so the button
    // no longer depends on picking a reward here — only disabled while a call is in flight.
    private void updateRedeemButtonState() {
        boolean isLoadActive = Boolean.TRUE.equals(viewModel.getIsLoading().getValue());
        btnRedeem.setEnabled(!isLoadActive);
        btnRedeem.setAlpha(isLoadActive ? 0.6f : 1f);
    }

    private void setEligibilityNeutral() {
        eligibilityChip.setText("Select a reward");
        eligibilityChip.setChipBackgroundColorResource(android.R.color.transparent);
        eligibilityChip.setChipStrokeWidth(1f);
        eligibilityChip.setChipStrokeColorResource(android.R.color.darker_gray);
        eligibilityChip.setTextColor(ContextCompat.getColor(this, android.R.color.black));
    }

    private void showRedeemResultDialog(String payload) {
        new AlertDialog.Builder(this)
                .setTitle("Redemption complete")
                .setMessage(payload)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
