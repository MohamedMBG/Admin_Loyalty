package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
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
    private RangeSlider sliderAge;
    private TextView tvAgeRange;
    private TextView tvTargetCount;

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
        etTitle       = v.findViewById(R.id.et_title);
        etMessage     = v.findViewById(R.id.et_message);
        switchAll     = v.findViewById(R.id.switch_send_all);
        sliderAge     = v.findViewById(R.id.slider_age_range);
        tvAgeRange    = v.findViewById(R.id.tv_age_range);
        tvTargetCount = v.findViewById(R.id.tv_target_count);

        TextView btnResetAge     = v.findViewById(R.id.btn_reset_age);
        MaterialButton btnRefreshCount = v.findViewById(R.id.btn_refresh_count);
        MaterialButton btnPreview      = v.findViewById(R.id.btn_preview);
        MaterialButton btnSend         = v.findViewById(R.id.btn_send);

        // Age slider label
        if (sliderAge != null && tvAgeRange != null) {
            sliderAge.addOnChangeListener((slider, value, fromUser) -> {
                int min = Math.round(slider.getValues().get(0));
                int max = Math.round(slider.getValues().get(1));
                tvAgeRange.setText(String.format(Locale.getDefault(), "%d - %d years", min, max));
            });
        }

        // Reset age
        if (btnResetAge != null && sliderAge != null) {
            btnResetAge.setOnClickListener(x -> sliderAge.setValues(18f, 65f));
        }

        // Preview count
        if (btnRefreshCount != null) {
            btnRefreshCount.setOnClickListener(x -> refreshRecipientCount());
        }

        // Preview dialog
        if (btnPreview != null) {
            btnPreview.setOnClickListener(x -> {
                String title = safeText(etTitle);
                String message = safeText(etMessage);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Preview")
                        .setMessage("Title:\n" + title + "\n\nMessage:\n" + message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }

        // Send push
        if (btnSend != null) {
            btnSend.setOnClickListener(x -> sendPush());
        }
    }

    // ---------------------------
    // Networking helpers
    // ---------------------------

    private void refreshRecipientCount() {
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
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String resp = Objects.requireNonNull(response.body()).string();
                        try {
                            JSONObject obj = new JSONObject(resp);
                            final int count = obj.optInt("count", 0);
                            requireActivity().runOnUiThread(() -> {
                                if (tvTargetCount != null) tvTargetCount.setText(String.valueOf(count));
                            });
                        } catch (Exception ex) {
                            runToast("Bad preview response");
                        }
                    }
                });
            });
        } catch (Exception e) {
            runToast(e.getMessage());
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
                        runToast("Sent: " + resp);
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
            // If "Send to all" is ON, return empty filters (backend targets all)
            if (switchAll != null && switchAll.isChecked()) {
                return filters;
            }

            // Example filter placeholders:
            // filters.put("platform", "android");     // add UI control later if needed
            // filters.put("role", "client");          // add UI control later if needed
            // filters.put("minPoints", 100);          // if you add points pickers
            // filters.put("maxPoints", 1000);

            // Age filters (only effective if you store age on devices)
            if (sliderAge != null && sliderAge.getValues() != null && sliderAge.getValues().size() >= 2) {
                int min = Math.round(sliderAge.getValues().get(0));
                int max = Math.round(sliderAge.getValues().get(1));
                filters.put("minAge", min);
                filters.put("maxAge", max);
            }

            // Topics example (if you add a topics UI):
            // JSONArray topics = new JSONArray();
            // topics.put("promo");
            // filters.put("topics", topics);

        } catch (JSONException ignored) {}
        return filters;
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
        // Optionally also update a status TextView/snackbar here.
    }

    /**
     * Gets a fresh Firebase ID token if available, then passes it to the consumer.
     * If the user is not signed in, we call back with null (backend accepts no Authorization).
     */
    private void withIdToken(Consumer<String> useToken) {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) { useToken.accept(null); return; }
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
