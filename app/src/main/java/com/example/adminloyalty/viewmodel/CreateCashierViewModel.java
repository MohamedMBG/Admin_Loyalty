package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.CreateCashierRepository;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CreateCashierViewModel extends ViewModel {

    private final CreateCashierRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> success = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public CreateCashierViewModel(CreateCashierRepository repository, @IoExecutor ExecutorService io) {
        this.repository = repository;
        this.io = io;
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getSuccess() { return success; }
    public LiveData<String> getError() { return error; }

    public void createCashier(String name, String email, String password) {
        loading.setValue(true);
        io.execute(() -> {
            ApiResult r = repository.createCashier(name, email, password);
            loading.postValue(false);
            if (r.isOk()) {
                success.postValue("Cashier Account Created!");
            } else {
                error.postValue(mapError(r));
            }
        });
    }

    private String mapError(ApiResult r) {
        if ("INVALID_CASHIER".equals(r.code)) return "Check the email and password (min 6 characters).";
        if ("CASHIER_EMAIL_EXISTS".equals(r.code)) return "That email is already registered.";
        return ApiErrors.message(r, "Not authorized. Admin role required.", "Creation failed");
    }

    // Reset success/error messages so they don't fire again on rotation
    public void resetStatus() {
        success.setValue(null);
        error.setValue(null);
    }
}
