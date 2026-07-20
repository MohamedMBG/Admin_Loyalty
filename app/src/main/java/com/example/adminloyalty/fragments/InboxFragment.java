package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.viewmodel.InboxViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * InboxFragment — UI layer for composing and sending push notifications.
 *
 * MVVM responsibilities:
 *   - Fragment:   View binding, click listeners, reading UI state, observing LiveData
 *   - ViewModel:  Orchestrates preview/send calls, exposes LiveData for UI state
 *   - Repository: Raw HTTP calls to push API, Firebase Auth token management
 *
 * The Fragment NEVER makes network calls directly. It reads the current filter
 * state from chips/sliders/switches, builds a JSONObject, and passes it to the
 * ViewModel. The ViewModel delegates to the Repository and posts results back
 * via LiveData, which the Fragment observes.
 */
@AndroidEntryPoint
public class InboxFragment extends Fragment {

    // ──────────────────────────────────────────────
    // 1. VIEW REFERENCES
    //    These are the UI widgets we need to read from or write to.
    //    They are bound in initViews() and used throughout the Fragment.
    // ──────────────────────────────────────────────

    private TextInputEditText etTitle, etMessage;
    private SwitchMaterial switchAll, switchBirthdayToday;
    private RangeSlider sliderAge;
    private TextView tvAgeRange, tvTargetCount, btnResetAge;
    private View filtersContainer, sendAllRow;
    private ImageView btnRefreshCount, btnBack;
    private Chip chipMale, chipFemale;
    private Chip chipLocHassan, chipLocAgdal, chipLocIrfane, chipLocOther;
    private Chip chipInterestCoffee, chipInterestTea, chipInterestPastries,
            chipInterestBreakfast, chipInterestLunch;
    private Chip chipVisit3days, chipVisitWeek, chipVisitMonth,
            chipVisitInactiveMonth, chipVisitInactive3Months;
    private MaterialButton btnPreview, btnSend;

    // ──────────────────────────────────────────────
    // 2. VIEWMODEL REFERENCE
    //    The ViewModel survives configuration changes (rotation).
    //    We never put View references inside it.
    // ──────────────────────────────────────────────

    private InboxViewModel viewModel;

    public InboxFragment() { /* Required empty constructor */ }

    // ──────────────────────────────────────────────
    // 3. LIFECYCLE: onCreateView
    //    Inflate the layout and bind all view references.
    //    This is called BEFORE onViewCreated.
    // ──────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_inbox, container, false);
        initViews(v);
        return v;
    }

    // ──────────────────────────────────────────────
    // 4. LIFECYCLE: onViewCreated
    //    The view is fully created — safe to initialize the ViewModel
    //    and start observing LiveData. We also set up click listeners
    //    that delegate to the ViewModel.
    // ──────────────────────────────────────────────

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        // Initialize the ViewModel through Hilt — Hilt looks at the @Inject
        // constructor of InboxViewModel and automatically provides InboxRepository.
        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);

        // Wire up LiveData observers so the UI reacts to data changes
        observeViewModel();

        // Set up click listeners that need the ViewModel
        setupActions();
    }

    // ──────────────────────────────────────────────
    // 5. OBSERVE VIEWMODEL
    //    Each LiveData from the ViewModel maps to a UI update.
    //    We use getViewLifecycleOwner() so observers are automatically
    //    removed when the Fragment's view is destroyed.
    // ──────────────────────────────────────────────

    private void observeViewModel() {
        // When the API returns a recipient count, display it
        viewModel.getRecipientCount().observe(getViewLifecycleOwner(), count -> {
            if (tvTargetCount != null) {
                tvTargetCount.setText(String.valueOf(count));
            }
        });

        // Show "…" loading indicator while previewing
        viewModel.getIsPreviewing().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading) && tvTargetCount != null) {
                tvTargetCount.setText("…");
            }
        });

        // Disable send button and show "Sending..." while push is in flight
        viewModel.getIsSending().observe(getViewLifecycleOwner(), sending -> {
            if (btnSend != null) {
                btnSend.setEnabled(!Boolean.TRUE.equals(sending));
                btnSend.setText(Boolean.TRUE.equals(sending) ? "Sending..." : "Send");
            }
        });

        // Show success toast when push completes
        viewModel.getSendResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null && !result.isEmpty()) {
                Toast.makeText(getContext(), result, Toast.LENGTH_SHORT).show();
                viewModel.clearSendResult();
            }
        });

        // Show error toast and reset the count display on failure
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
                // Reset count display if preview failed
                if (tvTargetCount != null && !Boolean.TRUE.equals(viewModel.getIsPreviewing().getValue())) {
                    tvTargetCount.setText("--");
                }
            }
        });
    }

    // ──────────────────────────────────────────────
    // 6. SETUP ACTIONS
    //    Click listeners that need the ViewModel are set up here
    //    (separate from initViews because viewModel isn't available
    //    during onCreateView — it's created in onViewCreated).
    // ──────────────────────────────────────────────

    private void setupActions() {
        // Refresh recipient count: read current filter state → pass to ViewModel
        if (btnRefreshCount != null) {
            btnRefreshCount.setOnClickListener(x ->
                    viewModel.previewRecipientCount(buildFilters())
            );
        }

        // Preview dialog: purely local UI — no network call needed
        if (btnPreview != null) {
            btnPreview.setOnClickListener(x -> {
                String title = safeText(etTitle);
                String message = safeText(etMessage);

                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                    Toast.makeText(getContext(), "Please enter a title and a message first.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Preview")
                        .setMessage("Title:\n" + title + "\n\nMessage:\n" + message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }

        // Send push: validate inputs → read filters → delegate to ViewModel
        if (btnSend != null) {
            btnSend.setOnClickListener(x -> {
                String title = safeText(etTitle);
                String message = safeText(etMessage);

                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                    Toast.makeText(getContext(), "Title and message are required", Toast.LENGTH_SHORT).show();
                    return;
                }

                viewModel.sendPush(title, message, buildFilters());
            });
        }
    }

    // ──────────────────────────────────────────────
    // 7. VIEW BINDING
    //    Find all views by ID and set up UI-only interactions
    //    (back button, slider labels, switch toggles).
    //    These are pure UI concerns — no business logic here.
    // ──────────────────────────────────────────────

    private void initViews(View v) {
        // --- Text inputs ---
        etTitle   = v.findViewById(R.id.et_title);
        etMessage = v.findViewById(R.id.et_message);

        // --- Switches ---
        switchAll           = v.findViewById(R.id.switch_send_all);
        switchBirthdayToday = v.findViewById(R.id.switch_birthday_today);

        // --- Age range slider ---
        sliderAge    = v.findViewById(R.id.slider_age_range);
        tvAgeRange   = v.findViewById(R.id.tv_age_range);
        btnResetAge  = v.findViewById(R.id.btn_reset_age);

        // --- Target count display ---
        tvTargetCount = v.findViewById(R.id.tv_target_count);

        // --- Filter container (hidden when "send to all" is on) ---
        filtersContainer = v.findViewById(R.id.ll_filters_container);

        // --- Gender chips ---
        chipMale   = v.findViewById(R.id.chip_male);
        chipFemale = v.findViewById(R.id.chip_female);

        // --- Location chips ---
        chipLocHassan = v.findViewById(R.id.chip_loc_casablanca);
        chipLocAgdal  = v.findViewById(R.id.chip_loc_rabat);
        chipLocIrfane = v.findViewById(R.id.chip_loc_marrakech);
        chipLocOther  = v.findViewById(R.id.chip_loc_other);

        // --- Behavioral interest chips ---
        chipInterestCoffee = v.findViewById(R.id.chip_interest_coffee);
        chipInterestTea = v.findViewById(R.id.chip_interest_tea);
        chipInterestPastries = v.findViewById(R.id.chip_interest_pastries);
        chipInterestBreakfast = v.findViewById(R.id.chip_interest_breakfast);
        chipInterestLunch = v.findViewById(R.id.chip_interest_lunch);

        // --- Last visit chips ---
        chipVisit3days = v.findViewById(R.id.chip_visit_3days);
        chipVisitWeek  = v.findViewById(R.id.chip_visit_week);
        chipVisitMonth = v.findViewById(R.id.chip_visit_month);
        chipVisitInactiveMonth = v.findViewById(R.id.chip_visit_inactive_month);
        chipVisitInactive3Months = v.findViewById(R.id.chip_visit_inactive_3months);

        // --- Action buttons ---
        btnRefreshCount = v.findViewById(R.id.btn_refresh_count);
        btnPreview      = v.findViewById(R.id.btn_preview);
        btnSend         = v.findViewById(R.id.btn_send);
        btnBack         = v.findViewById(R.id.btnBack);
        sendAllRow      = v.findViewById(R.id.ll_send_all);

        // ── Back button: navigate back ──
        if (btnBack != null) {
            btnBack.setOnClickListener(view ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        // ── Age slider: update the label text as the user drags ──
        if (sliderAge != null && tvAgeRange != null) {
            // Set initial label from XML defaults
            if (sliderAge.getValues() == null || sliderAge.getValues().size() < 2) {
                sliderAge.setValues(18f, 65f);
            }
            if (sliderAge.getValues() != null && sliderAge.getValues().size() >= 2) {
                int min = Math.round(sliderAge.getValues().get(0));
                int max = Math.round(sliderAge.getValues().get(1));
                tvAgeRange.setText(String.format(Locale.getDefault(), "%d - %d years", min, max));
            }

            // Update label on drag
            sliderAge.addOnChangeListener((slider, value, fromUser) -> {
                int min = Math.round(slider.getValues().get(0));
                int max = Math.round(slider.getValues().get(1));
                tvAgeRange.setText(String.format(Locale.getDefault(), "%d - %d years", min, max));
            });
        }

        // ── Reset age slider to defaults ──
        if (btnResetAge != null && sliderAge != null) {
            btnResetAge.setOnClickListener(x -> {
                sliderAge.setValues(18f, 65f);
                tvAgeRange.setText("18 - 65 years");
            });
        }

        // ── "Send to all" switch: hides/shows the filters section ──
        if (switchAll != null && filtersContainer != null) {
            toggleFiltersVisibility(switchAll.isChecked());

            switchAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                toggleFiltersVisibility(isChecked);
                if (isChecked) {
                    clearAllFilters();
                }
            });
        }

        // ── Tap entire row to toggle the "send to all" switch ──
        if (sendAllRow != null && switchAll != null) {
            sendAllRow.setOnClickListener(view ->
                    switchAll.setChecked(!switchAll.isChecked())
            );
        }
    }

    // ──────────────────────────────────────────────
    // 8. BUILD FILTERS
    //    Reads the current state of all chips/sliders/switches
    //    and produces a JSONObject the API expects.
    //
    //    This stays in the Fragment because it reads from Views.
    //    The ViewModel receives the finished JSONObject — it never
    //    touches any View directly.
    // ──────────────────────────────────────────────

    private JSONObject buildFilters() {
        JSONObject filters = new JSONObject();
        try {
            // If "send to all" is checked, no other filters apply
            if (switchAll != null && switchAll.isChecked()) {
                filters.put("sendToAll", true);
                return filters;
            }

            filters.put("sendToAll", false);

            // Age range from the slider
            if (sliderAge != null && sliderAge.getValues() != null && sliderAge.getValues().size() >= 2) {
                filters.put("minAge", Math.round(sliderAge.getValues().get(0)));
                filters.put("maxAge", Math.round(sliderAge.getValues().get(1)));
            }

            // Gender chips → ["male", "female"]
            JSONArray genders = new JSONArray();
            if (chipMale != null && chipMale.isChecked())     genders.put("male");
            if (chipFemale != null && chipFemale.isChecked()) genders.put("female");
            if (genders.length() > 0) filters.put("genders", genders);

            // Location chips → ["Hassan", "Agdal", "Al Irfane", "Other"]
            JSONArray locations = new JSONArray();
            if (chipLocHassan != null && chipLocHassan.isChecked())   locations.put("Hassan");
            if (chipLocAgdal != null && chipLocAgdal.isChecked())     locations.put("Agdal");
            if (chipLocIrfane != null && chipLocIrfane.isChecked())   locations.put("Al Irfane");
            if (chipLocOther != null && chipLocOther.isChecked())     locations.put("Other");
            if (locations.length() > 0) filters.put("locations", locations);

            // Top behavioral category collected from customer menu selections.
            JSONArray interests = new JSONArray();
            if (chipInterestCoffee != null && chipInterestCoffee.isChecked()) interests.put("coffee");
            if (chipInterestTea != null && chipInterestTea.isChecked()) interests.put("tea");
            if (chipInterestPastries != null && chipInterestPastries.isChecked()) interests.put("pastries");
            if (chipInterestBreakfast != null && chipInterestBreakfast.isChecked()) interests.put("breakfast");
            if (chipInterestLunch != null && chipInterestLunch.isChecked()) interests.put("lunch");
            if (interests.length() > 0) filters.put("interests", interests);

            // Last visit chips target either recent visitors or lapsed/never-visited members.
            int lastVisitDays = -1;
            if (chipVisit3days != null && chipVisit3days.isChecked())      lastVisitDays = 3;
            else if (chipVisitWeek != null && chipVisitWeek.isChecked())   lastVisitDays = 7;
            else if (chipVisitMonth != null && chipVisitMonth.isChecked()) lastVisitDays = 30;
            if (lastVisitDays > 0) filters.put("lastVisitWithinDays", lastVisitDays);
            if (chipVisitInactiveMonth != null && chipVisitInactiveMonth.isChecked()) {
                filters.put("lastVisitBeforeDays", 30);
            } else if (chipVisitInactive3Months != null && chipVisitInactive3Months.isChecked()) {
                filters.put("lastVisitBeforeDays", 90);
            }

            // Birthday today switch
            if (switchBirthdayToday != null && switchBirthdayToday.isChecked()) {
                filters.put("birthdayToday", true);
            }

        } catch (JSONException ignored) { }
        return filters;
    }

    // ──────────────────────────────────────────────
    // 9. UI HELPERS
    //    Pure UI manipulation — no business logic.
    //    These stay in the Fragment, never in the ViewModel.
    // ──────────────────────────────────────────────

    /** Hide all filter controls when "send to all" is enabled */
    private void toggleFiltersVisibility(boolean sendToAllChecked) {
        if (filtersContainer == null) return;
        filtersContainer.setVisibility(sendToAllChecked ? View.GONE : View.VISIBLE);
    }

    /** Reset every filter widget back to its default state */
    private void clearAllFilters() {
        if (sliderAge != null)          sliderAge.setValues(18f, 65f);
        if (tvAgeRange != null)         tvAgeRange.setText("18 - 65 years");

        if (chipMale != null)           chipMale.setChecked(false);
        if (chipFemale != null)         chipFemale.setChecked(false);

        if (chipLocHassan != null)      chipLocHassan.setChecked(false);
        if (chipLocAgdal != null)       chipLocAgdal.setChecked(false);
        if (chipLocIrfane != null)      chipLocIrfane.setChecked(false);
        if (chipLocOther != null)       chipLocOther.setChecked(false);

        if (chipInterestCoffee != null) chipInterestCoffee.setChecked(false);
        if (chipInterestTea != null) chipInterestTea.setChecked(false);
        if (chipInterestPastries != null) chipInterestPastries.setChecked(false);
        if (chipInterestBreakfast != null) chipInterestBreakfast.setChecked(false);
        if (chipInterestLunch != null) chipInterestLunch.setChecked(false);

        if (chipVisit3days != null)     chipVisit3days.setChecked(false);
        if (chipVisitWeek != null)      chipVisitWeek.setChecked(false);
        if (chipVisitMonth != null)     chipVisitMonth.setChecked(false);
        if (chipVisitInactiveMonth != null) chipVisitInactiveMonth.setChecked(false);
        if (chipVisitInactive3Months != null) chipVisitInactive3Months.setChecked(false);

        if (switchBirthdayToday != null) switchBirthdayToday.setChecked(false);
    }

    /** Safely extract trimmed text from an EditText (prevents NPE) */
    private String safeText(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
