package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.adapters.RewardAdminAdapter;
import com.example.adminloyalty.models.RewardItem;
import com.example.adminloyalty.viewmodel.RewardsAdminViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RewardsAdminFragment extends Fragment {

    private RecyclerView rvRewards;
    private ExtendedFloatingActionButton fabAdd;
    private MaterialToolbar toolbar;
    private RewardAdminAdapter adapter;

    private RewardsAdminViewModel viewModel;

    private final String[] FAMILIES = {"Coffee", "Tea", "Pastries", "Cold Drinks", "Merchandise", "Others"};

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_rewards_admin, container, false);

        rvRewards = v.findViewById(R.id.rvRewards);
        fabAdd = v.findViewById(R.id.fabAddReward);
        toolbar = v.findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(view -> requireActivity().onBackPressed());

        setupRecyclerView();

        viewModel = new ViewModelProvider(this).get(RewardsAdminViewModel.class);
        observeViewModel();

        fabAdd.setOnClickListener(view -> showEditorDialog(null));

        return v;
    }

    private void observeViewModel() {
        viewModel.getRewards().observe(getViewLifecycleOwner(), rewards -> {
            if (rewards != null) {
                adapter.setItems(rewards);
            }
        });

        viewModel.getActionStatus().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.resetStatus();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new RewardAdminAdapter();
        rvRewards.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRewards.setAdapter(adapter);

        adapter.setListener(new RewardAdminAdapter.OnRewardActionListener() {
            @Override
            public void onEdit(RewardItem item) {
                showEditorDialog(item);
            }

            @Override
            public void onDelete(RewardItem item) {
                confirmDelete(item);
            }
        });
    }

    private void showEditorDialog(@Nullable RewardItem itemToEdit) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.editor_layout, null);
        sheet.setContentView(view);

        TextInputEditText etName = view.findViewById(R.id.inputRewardName);
        TextInputEditText etPoints = view.findViewById(R.id.inputPoints);
        AutoCompleteTextView dropCategory = view.findViewById(R.id.inputCategory);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveReward);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, FAMILIES);
        dropCategory.setAdapter(catAdapter);

        if (itemToEdit != null) {
            etName.setText(itemToEdit.getName());
            etPoints.setText(String.valueOf(itemToEdit.getCostPoints()));
            dropCategory.setText(itemToEdit.getCategory(), false);
            btnSave.setText("Update Gift");
        } else {
            btnSave.setText("Add Gift");
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String cat = dropCategory.getText().toString().trim();
            String pointsStr = etPoints.getText().toString().trim();

            if (name.isEmpty() || cat.isEmpty() || pointsStr.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int points = Integer.parseInt(pointsStr);
            RewardItem newItem = new RewardItem(name, cat, points);

            if (itemToEdit == null) {
                viewModel.addReward(newItem);
            } else {
                viewModel.updateReward(itemToEdit.getId(), newItem);
            }
            sheet.dismiss();
        });

        sheet.show();
    }

    private void confirmDelete(RewardItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Gift?")
                .setMessage("Are you sure you want to remove " + item.getName() + " from the catalog?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteReward(item.getId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}