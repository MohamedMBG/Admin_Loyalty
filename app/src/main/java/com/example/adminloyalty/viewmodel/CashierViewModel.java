package com.example.adminloyalty.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.CashierRepository;
import com.example.adminloyalty.data.api.ApiResult;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CashierViewModel extends ViewModel {

    // ponytail: client-side MAD->points, ratio constant. Settings doc read isn't rules-allowed;
    // switch to points-direct input, or a backend-owned ratio, if pricing needs to move server-side.
    private static final double POINTS_RATIO = 5.0;

    private final CashierRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<String> cashierName = new MutableLiveData<>();
    private final MutableLiveData<String> cashierId = new MutableLiveData<>();

    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("Ready");
    private final MutableLiveData<String> qrMetaMessage = new MutableLiveData<>("Generate a QR to begin");

    private final MutableLiveData<Boolean> isIssuing = new MutableLiveData<>(false);
    private final MutableLiveData<String> voucherIdLiveData = new MutableLiveData<>();

    private final MutableLiveData<String> timerText = new MutableLiveData<>("--:--");
    private final MutableLiveData<Boolean> confirmDialogVisible = new MutableLiveData<>(false);

    private CountDownTimer countdown;
    private String activeCode; // code minted by backend, needed to revoke
    private final int currentValidForSec = 120;

    @Inject
    public CashierViewModel(CashierRepository repository, ExecutorService io) {
        this.repository = repository;
        this.io = io;
        initCashierMeta();
    }

    public LiveData<String> getCashierName() { return cashierName; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<String> getQrMetaMessage() { return qrMetaMessage; }
    public LiveData<Boolean> getIsIssuing() { return isIssuing; }
    public LiveData<String> getVoucherId() { return voucherIdLiveData; }
    public LiveData<String> getTimerText() { return timerText; }

    private void initCashierMeta() {
        String uid = repository.getCurrentUserId();
        if (uid == null) {
            error.setValue("Error: no cashier session. Please log in again.");
            return;
        }

        cashierId.setValue(uid);
        String email = repository.getCurrentUserEmail();
        String defaultName = (email != null && !email.trim().isEmpty()) ? email : "Unknown Cashier";

        repository.getUserProfile(uid)
                .addOnSuccessListener(snap -> {
                    if (snap.exists() && snap.getString("name") != null && !snap.getString("name").trim().isEmpty()) {
                        cashierName.setValue(snap.getString("name"));
                    } else {
                        cashierName.setValue(defaultName);
                    }
                })
                .addOnFailureListener(e -> cashierName.setValue(defaultName));
    }

    public void createVoucher(String orderNo, String amtStr) {
        if (cashierId.getValue() == null) {
            error.setValue("Cashier not loaded yet. Please try again in a moment.");
            return;
        }

        if (amtStr.isEmpty()) {
            error.setValue("Missing amount");
            return;
        }

        double amountMAD;
        try {
            amountMAD = Double.parseDouble(amtStr);
            if (amountMAD <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            error.setValue("Invalid amount");
            return;
        }

        int points = (int) Math.max(1, Math.round(amountMAD / POINTS_RATIO));

        isIssuing.setValue(true);
        statusMessage.setValue("Creating...");

        io.execute(() -> {
            ApiResult result = repository.createEarnCode(points);
            if (result.isOk()) {
                String code = result.data != null
                        ? result.data.optString("code", result.data.optString("id", null))
                        : null;
                if (code == null) {
                    isIssuing.postValue(false);
                    statusMessage.postValue("Error");
                    error.postValue("Backend returned no code");
                    return;
                }
                activeCode = code;
                voucherIdLiveData.postValue(code);
                qrMetaMessage.postValue("Show to customer to scan");
                isIssuing.postValue(false);
                statusMessage.postValue("Active");
                // ponytail: local expiry timer for UX; backend enforces the real expiry. Wire the
                // server's expiresAt here once its exact type (epoch ms vs ISO) is confirmed.
                startCountdown(currentValidForSec * 1000L);
            } else {
                isIssuing.postValue(false);
                statusMessage.postValue("Error");
                error.postValue(mapError(result));
            }
        });
    }

    private void startCountdown(long durationMs) {
        cancelTimer();
        if (durationMs < 0) durationMs = 0;

        final long dur = durationMs;
        // CountDownTimer must be created on the main thread.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            countdown = new CountDownTimer(dur, 1000) {
                @Override public void onTick(long left) {
                    long s = left / 1000, m = s / 60, r = s % 60;
                    timerText.setValue(String.format("%02d:%02d", m, r));
                }

                @Override public void onFinish() {
                    statusMessage.setValue("Expired");
                    qrMetaMessage.setValue("QR expired. Generate a new one.");
                    timerText.setValue("00:00");
                    voucherIdLiveData.setValue(null); // Clear QR
                    activeCode = null;
                }
            }.start();
        });
    }

    public void cancelActive() {
        final String code = activeCode;
        if (code != null) {
            io.execute(() -> {
                ApiResult r = repository.revokeEarnCode(code); // best-effort revoke
                if (!r.isOk()) {
                    android.util.Log.w("CashierViewModel",
                            "Revoke failed for " + code + ": " + r.code + " " + r.message);
                }
            });
        }
        teardownAndReset();
    }

    public void teardownAndReset() {
        cancelTimer();
        activeCode = null;
        isIssuing.setValue(false);
        voucherIdLiveData.setValue(null);
        qrMetaMessage.setValue("Generate a QR to begin");
        statusMessage.setValue("Ready");
        timerText.setValue("--:--");
    }

    private void cancelTimer() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    /** Map the backend error envelope to a cashier-facing message. */
    private String mapError(ApiResult r) {
        if (r.code == null) return "Request failed";
        switch (r.code) {
            case "NETWORK_ERROR": return "No connection. Check your network and retry.";
            case "FORBIDDEN":
            case "HTTP_403":      return "Not authorized. Admin role required.";
            case "RATE_LIMITED":  return "Too many requests. Try again shortly.";
            default:              return r.message != null ? r.message : "Failed to create code";
        }
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
        // io is app-scoped (shared @Singleton); do not shut it down here.
    }
}
