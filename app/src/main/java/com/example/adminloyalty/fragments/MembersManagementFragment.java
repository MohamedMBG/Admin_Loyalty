package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.MainActivity;
import com.example.adminloyalty.R;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

public class MembersManagementFragment extends Fragment {

    private TextInputEditText etSearch;
    private ChipGroup chipGroup;
    private RecyclerView rvMembers;
    private View emptyState;

    public MembersManagementFragment() { /* Required empty */ }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_members_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Top bar actions
        ImageView ivMenu = view.findViewById(R.id.iv_menu);
        ivMenu.setOnClickListener(v -> ((MainActivity) requireActivity()).openDrawer());
        ImageView addMember = view.findViewById(R.id.btn_add_member);



        if (addMember != null) {
            addMember.setOnClickListener(v -> {
                // TODO: open add-member dialog / screen
            });
        }

        // Content views
        etSearch = view.findViewById(R.id.et_search);
        chipGroup = view.findViewById(R.id.chip_group_filters);
        rvMembers = view.findViewById(R.id.rv_members);
        emptyState = view.findViewById(R.id.empty_state);

        // TODO: set up RecyclerView adapter + query members
        // TODO: toggle emptyState visibility depending on data
    }
}
