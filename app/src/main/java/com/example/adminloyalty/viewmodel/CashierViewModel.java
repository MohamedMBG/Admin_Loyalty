package com.example.adminloyalty.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.CashierRepository;
import com.google.firebase.firestore.ListenerRegistration;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CashierViewModel extends ViewModel {

    private final CashierRepository repository;

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
    private ListenerRegistration voucherListener;
    private final int currentValidForSec = 120;

    @Inject
    public CashierViewModel(CashierRepository repository) {
        this.repository = repository;
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

        if (orderNo.isEmpty() || amtStr.isEmpty()) {
            error.setValue("Missing inputs");
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

        isIssuing.setValue(true);
        statusMessage.setValue("Checking...");

        repository.checkReceiptExists(orderNo)
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        isIssuing.setValue(false);
                        statusMessage.setValue("Blocked");
                        error.setValue("This receipt number already exists.");
                        return;
                    }

                    statusMessage.setValue("Creating...");

                    repository.createVoucherTransaction(orderNo, amountMAD, currentValidForSec, cashierId.getValue(), cashierName.getValue())
                            .addOnSuccessListener(id -> {
                                voucherIdLiveData.setValue(id);
                                qrMetaMessage.setValue("Show to customer to scan");
                                isIssuing.setValue(false);
                                statusMessage.setValue("Active");

                                startDocListener(id);
                                startCountdown(currentValidForSec * 1000L);
                            })
                            .addOnFailureListener(e -> {
                                isIssuing.setValue(false);
                                statusMessage.setValue("Error");
                                error.setValue("Transaction failed: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    isIssuing.setValue(false);
                    statusMessage.setValue("Error");
                    error.setValue("Check failed: " + e.getMessage());
                });
    }

    private void startDocListener(String voucherId) {
        removeDocListener();
        voucherListener = repository.getVoucherReference(voucherId).addSnapshotListener((snap, err) -> {
            if (err != null || snap == null || !snap.exists()) return;
            String status = snap.getString("status");
            if (status == null) return;

            statusMessage.setValue(status.toUpperCase());
            switch (status) {
                case "redeemed":
                    qrMetaMessage.setValue("Customer scanned successfully!");
                    voucherIdLiveData.setValue(null); // Clear QR
                    break;
                case "canceled":
                    qrMetaMessage.setValue("Canceled");
                    voucherIdLiveData.setValue(null); // Clear QR
                    break;
                default:
                    break;
            }
        });
    }

    private void startCountdown(long durationMs) {
        cancelTimer();
        if (durationMs < 0) durationMs = 0;

        countdown = new CountDownTimer(durationMs, 1000) {
            @Override public void onTick(long left) {
                long s = left / 1000, m = s / 60, r = s % 60;
                timerText.setValue(String.format("%02d:%02d", m, r));
            }

            @Override public void onFinish() {
                statusMessage.setValue("Expired");
                qrMetaMessage.setValue("QR expired. Generate a new one.");
                timerText.setValue("00:00");
                voucherIdLiveData.setValue(null); // Clear QR
            }
        }.start();
    }

    public void cancelActive() {
        String vid = voucherIdLiveData.getValue();
        if (vid != null) {
            repository.cancelVoucherStatus(vid)
                    .addOnCompleteListener(task -> teardownAndReset());
        } else {
            teardownAndReset();
        }
    }

    public void teardownAndReset() {
        removeDocListener();
        cancelTimer();
        isIssuing.setValue(false);
        voucherIdLiveData.setValue(null);
        qrMetaMessage.setValue("Generate a QR to begin");
        statusMessage.setValue("Ready");
        timerText.setValue("--:--");
    }

    private void removeDocListener() {
        if (voucherListener != null) {
            voucherListener.remove();
            voucherListener = null;
        }
    }

    private void cancelTimer() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        removeDocListener();
        cancelTimer();
    }
}
