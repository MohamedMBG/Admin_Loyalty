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

import com.example.adminloyalty.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InboxFragment extends Fragment {

    private TextInputEditText etTitle, etMessage;
    private SwitchMaterial switchAll;
    private SwitchMaterial switchBirthdayToday;
    private RangeSlider sliderAge;
    private TextView tvAgeRange;
    private TextView tvTargetCount;
    private View filtersContainer;

    // Chips
    private Chip chipMale, chipFemale;
    private Chip chipLocHassan, chipLocAgdal, chipLocIrfane, chipLocOther;
    private Chip chipVisit3days, chipVisitWeek, chipVisitMonth;

    // IMPORTANT: keep BASE without trailing "api/"; we append endpoints below.
    private static final String API_BASE = "https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient();

    public InboxFragment() { /* Required empty */ }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        // Main inputs
        etTitle       = v.findViewById(R.id.et_title);
        etMessage     = v.findViewById(R.id.et_message);
        switchAll     = v.findViewById(R.id.switch_send_all);
        sliderAge     = v.findViewById(R.id.slider_age_range);
        tvAgeRange    = v.findViewById(R.id.tv_age_range);
        tvTargetCount = v.findViewById(R.id.tv_target_count);
        filtersContainer = v.findViewById(R.id.ll_filters_container);
        switchBirthdayToday = v.findViewById(R.id.switch_birthday_today);

        // Gender chips
        chipMale   = v.findViewById(R.id.chip_male);
        chipFemale = v.findViewById(R.id.chip_female);

        // Location chips
        chipLocHassan  = v.findViewById(R.id.chip_loc_casablanca); // Hassan
        chipLocAgdal   = v.findViewById(R.id.chip_loc_rabat);      // Agdal
        chipLocIrfane  = v.findViewById(R.id.chip_loc_marrakech);  // Al Irfane
        chipLocOther   = v.findViewById(R.id.chip_loc_other);      // Other

        // Last visit chips
        chipVisit3days = v.findViewById(R.id.chip_visit_3days);
        chipVisitWeek  = v.findViewById(R.id.chip_visit_week);
        chipVisitMonth = v.findViewById(R.id.chip_visit_month);

        TextView btnResetAge       = v.findViewById(R.id.btn_reset_age);
        ImageView btnRefreshCount  = v.findViewById(R.id.btn_refresh_count);
        MaterialButton btnPreview  = v.findViewById(R.id.btn_preview);
        MaterialButton btnSend     = v.findViewById(R.id.btn_send);
        ImageView btnBack          = v.findViewById(R.id.btnBack);
        View sendAllRow            = v.findViewById(R.id.ll_send_all);

        // ---------- Back button ----------
        if (btnBack != null) {
            btnBack.setOnClickListener(view ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed()
            );
        }

        // ---------- Age slider + label ----------
        if (sliderAge != null && tvAgeRange != null) {
            // Init label with default values
            if (sliderAge.getValues() != null && sliderAge.getValues().size() >= 2) {
                int min = Math.round(sliderAge.getValues().get(0));
                int max = Math.round(sliderAge.getValues().get(1));
                tvAgeRange.setText(String.format(Locale.getDefault(), "%d - %d years", min, max));
            }

            sliderAge.addOnChangeListener((slider, value, fromUser) -> {
                int min = Math.round(slider.getValues().get(0));
                int max = Math.round(slider.getValues().get(1));
                tvAgeRange.setText(String.format(Locale.getDefault(), "%d - %d years", min, max));
            });
        }

        // Reset age
        if (btnResetAge != null && sliderAge != null) {
            btnResetAge.setOnClickListener(x -> {
                sliderAge.setValues(18f, 65f);
                tvAgeRange.setText("18 - 65 years");
            });
        }

        // ---------- "Send to all" switch toggles filters ----------
        if (switchAll != null && filtersContainer != null) {
            // Initialise: when checked -> hide filters
            toggleFiltersVisibility(switchAll.isChecked());

            switchAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                toggleFiltersVisibility(isChecked);
                // Clear filters when toggling back to "all"
                if (isChecked) {
                    clearAllFilters();
                }
            });
        }

        // Tap on the whole row to toggle switch
        if (sendAllRow != null && switchAll != null) {
            sendAllRow.setOnClickListener(view ->
                    switchAll.setChecked(!switchAll.isChecked())
            );
        }

        // ---------- Target count preview ----------
        if (btnRefreshCount != null) {
            btnRefreshCount.setOnClickListener(x -> refreshRecipientCount());
        }

        // ---------- Preview dialog ----------
        if (btnPreview != null) {
            btnPreview.setOnClickListener(x -> {
                String title = safeText(etTitle);
                String message = safeText(etMessage);

                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                    runToast("Please enter a title and a message first.");
                    return;
                }

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Preview")
                        .setMessage("Title:\n" + title + "\n\nMessage:\n" + message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }

        // ---------- Send push ----------
        if (btnSend != null) {
            btnSend.setOnClickListener(x -> sendPush());
        }
    }

    // ---------------------------
    // Networking helpers
    // ---------------------------

    private void refreshRecipientCount() {
        if (tvTargetCount != null) {
            tvTargetCount.setText("…"); // loading state
        }

        try {
            JSONObject body = new JSONObject();
            body.put("filters", buildFilters());

            RequestBody reqBody = RequestBody.create(body.toString(), JSON);
            withIdToken(idToken -> {
                Request.Builder rb = new Request.Builder()
                        .url(API_BASE + "/api/push/preview")
                        .post(reqBody)
                        .addHeader("Content-Type", "application/json");
                if (idToken != null) rb.addHeader("Authorization", "Bearer " + idToken);

                http.newCall(rb.build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runToast("Preview failed: " + e.getMessage());
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (tvTargetCount != null) tvTargetCount.setText("--");
                        });
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String resp = Objects.requireNonNull(response.body()).string();
                        if (!response.isSuccessful()) {
                            runToast("Preview error: " + response.code());
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                if (tvTargetCount != null) tvTargetCount.setText("--");
                            });
                            return;
                        }
                        try {
                            JSONObject obj = new JSONObject(resp);
                            final int count = obj.optInt("count", 0);
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                if (tvTargetCount != null) {
                                    tvTargetCount.setText(String.valueOf(count));
                                }
                            });
                        } catch (Exception ex) {
                            runToast("Bad preview response");
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                if (tvTargetCount != null) tvTargetCount.setText("--");
                            });
                        }
                    }
                });
            });
        } catch (Exception e) {
            runToast(e.getMessage());
            if (tvTargetCount != null) tvTargetCount.setText("--");
        }
    }

    private void sendPush() {
        String title = safeText(etTitle);
        String message = safeText(etMessage);

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
            runToast("Title and message are required");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("title", title);
            body.put("message", message);
            body.put("filters", buildFilters());

            // Optional extra payload (deep link, screen, etc.)
            // JSONObject data = new JSONObject();
            // data.put("deepLink", "app://promo/cheesecake");
            // body.put("data", data);

            RequestBody reqBody = RequestBody.create(body.toString(), JSON);

            withIdToken(idToken -> {
                Request.Builder rb = new Request.Builder()
                        .url(API_BASE + "/api/push/send")
                        .post(reqBody)
                        .addHeader("Content-Type", "application/json");
                if (idToken != null) rb.addHeader("Authorization", "Bearer " + idToken);

                http.newCall(rb.build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runToast("Send failed: " + e.getMessage());
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String resp = Objects.requireNonNull(response.body()).string();
                        if (!response.isSuccessful()) {
                            runToast("Send error: " + response.code());
                            return;
                        }
                        runToast("Push sent successfully");
                    }
                });
            });
        } catch (Exception e) {
            runToast(e.getMessage());
        }
    }

    // ---------------------------
    // Filters builder
    // ---------------------------

    private JSONObject buildFilters() {
        JSONObject filters = new JSONObject();
        try {
            // "Send to all" = backend targets all
            if (switchAll != null && switchAll.isChecked()) {
                filters.put("sendToAll", true);
                return filters;
            }

            filters.put("sendToAll", false);

            // Age filters
            if (sliderAge != null && sliderAge.getValues() != null && sliderAge.getValues().size() >= 2) {
                int min = Math.round(sliderAge.getValues().get(0));
                int max = Math.round(sliderAge.getValues().get(1));
                filters.put("minAge", min);
                filters.put("maxAge", max);
            }

            // Gender filters -> ["male","female"]
            JSONArray genders = new JSONArray();
            if (chipMale != null && chipMale.isChecked())   genders.put("male");
            if (chipFemale != null && chipFemale.isChecked()) genders.put("female");
            if (genders.length() > 0) {
                filters.put("genders", genders);
            }

            // Location filters -> ["Hassan","Agdal","Al Irfane","Other"]
            JSONArray locations = new JSONArray();
            if (chipLocHassan != null && chipLocHassan.isChecked())  locations.put("Hassan");
            if (chipLocAgdal != null && chipLocAgdal.isChecked())    locations.put("Agdal");
            if (chipLocIrfane != null && chipLocIrfane.isChecked())  locations.put("Al Irfane");
            if (chipLocOther != null && chipLocOther.isChecked())    locations.put("Other");
            if (locations.length() > 0) {
                filters.put("locations", locations);
            }

            // Last visit (mutually exclusive chips -> convert to "lastVisitDays")
            int lastVisitDays = -1;
            if (chipVisit3days != null && chipVisit3days.isChecked()) {
                lastVisitDays = 3;
            } else if (chipVisitWeek != null && chipVisitWeek.isChecked()) {
                lastVisitDays = 7;
            } else if (chipVisitMonth != null && chipVisitMonth.isChecked()) {
                lastVisitDays = 30;
            }
            if (lastVisitDays > 0) {
                filters.put("lastVisitDays", lastVisitDays);
            }

            // Birthday today
            if (switchBirthdayToday != null && switchBirthdayToday.isChecked()) {
                filters.put("birthdayToday", true);
            }

        } catch (JSONException ignored) { }
        return filters;
    }

    // Clear all UI filters when switching back to "send to all"
    private void clearAllFilters() {
        // Reset age slider
        if (sliderAge != null) {
            sliderAge.setValues(18f, 65f);
        }
        if (tvAgeRange != null) {
            tvAgeRange.setText("18 - 65 years");
        }

        // Gender
        if (chipMale != null)   chipMale.setChecked(false);
        if (chipFemale != null) chipFemale.setChecked(false);

        // Locations
        if (chipLocHassan != null) chipLocHassan.setChecked(false);
        if (chipLocAgdal != null)  chipLocAgdal.setChecked(false);
        if (chipLocIrfane != null) chipLocIrfane.setChecked(false);
        if (chipLocOther != null)  chipLocOther.setChecked(false);

        // Last visit
        if (chipVisit3days != null) chipVisit3days.setChecked(false);
        if (chipVisitWeek != null)  chipVisitWeek.setChecked(false);
        if (chipVisitMonth != null) chipVisitMonth.setChecked(false);

        // Birthday
        if (switchBirthdayToday != null) switchBirthdayToday.setChecked(false);
    }

    private void toggleFiltersVisibility(boolean sendToAllChecked) {
        if (filtersContainer == null) return;
        filtersContainer.setVisibility(sendToAllChecked ? View.GONE : View.VISIBLE);
    }

    // ---------------------------
    // Utilities
    // ---------------------------

    private String safeText(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private void runToast(String msg) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Gets a fresh Firebase ID token if available, then passes it to the consumer.
     * If the user is not signed in, we call back with null (backend accepts no Authorization).
     */
    private void withIdToken(Consumer<String> useToken) {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) {
                useToken.accept(null);
                return;
            }
            u.getIdToken(true).addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    useToken.accept(null);
                    return;
                }
                useToken.accept(task.getResult().getToken());
            });
        } catch (Exception e) {
            useToken.accept(null);
        }
    }
}
