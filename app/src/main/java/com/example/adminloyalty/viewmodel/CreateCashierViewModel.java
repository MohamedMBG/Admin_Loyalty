package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.CreateCashierRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CreateCashierViewModel extends ViewModel {

    private final CreateCashierRepository repository;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> success = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public CreateCashierViewModel(CreateCashierRepository repository) {
        this.repository = repository;
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getSuccess() { return success; }
    public LiveData<String> getError() { return error; }

    public void createCashier(String name, String email, String password) {
        loading.setValue(true);
        repository.createCashierAuth(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveRecord(uid, name, email);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Creation Failed: " + e.getMessage());
                    loading.setValue(false);
                });
    }

    private void saveRecord(String uid, String name, String email) {
        repository.saveCashierToFirestore(uid, name, email)
                .addOnSuccessListener(aVoid -> {
                    success.setValue("Cashier Account Created!");
                    loading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    error.setValue("DB Error: " + e.getMessage());
                    loading.setValue(false);
                });
    }

    // Reset success/error messages so they don't fire again on rotation
    public void resetStatus() {
        success.setValue(null);
        error.setValue(null);
    }
}
