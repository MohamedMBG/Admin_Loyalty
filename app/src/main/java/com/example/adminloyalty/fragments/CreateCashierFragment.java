package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.adminloyalty.R;
import com.example.adminloyalty.viewmodel.CreateCashierViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CreateCashierFragment extends Fragment {

    private TextInputEditText etName, etEmail, etPassword;
    private MaterialButton btnCreate;
    private ProgressBar progressBar;
    private ImageView btnBack;

    private CreateCashierViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create_cashier, container, false);

        initViews(v);

        viewModel = new ViewModelProvider(this).get(CreateCashierViewModel.class);
        observeViewModel();

        return v;
    }

    private void initViews(View v) {
        etName = v.findViewById(R.id.etName);
        etEmail = v.findViewById(R.id.etEmail);
        etPassword = v.findViewById(R.id.etPassword);
        btnCreate = v.findViewById(R.id.btnCreate);
        progressBar = v.findViewById(R.id.progressBar);
        btnBack = v.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(view -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        btnCreate.setOnClickListener(view -> validateAndCreate());
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), this::setLoading);

        viewModel.getSuccess().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                clearForm();
                viewModel.resetStatus();
                if (getActivity() != null) getActivity().onBackPressed();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                viewModel.resetStatus();
            }
        });
    }

    private void validateAndCreate() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError(getString(R.string.error_name_required));
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.error_email_required));
            return;
        }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            etPassword.setError(getString(R.string.error_password_short));
            return;
        }

        viewModel.createCashier(name, email, pass);
    }

    private void setLoading(Boolean isLoading) {
        if (isLoading == null) return;
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(!isLoading);
        etName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }

    private void clearForm() {
        etName.setText("");
        etEmail.setText("");
        etPassword.setText("");
    }
}
