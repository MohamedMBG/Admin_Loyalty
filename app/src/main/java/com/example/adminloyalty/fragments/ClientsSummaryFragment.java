package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.adapters.ClientAdapter;
import com.example.adminloyalty.viewmodel.ClientsSummaryViewModel;

import java.util.ArrayList;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ClientsSummaryFragment extends Fragment {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvClients;
    private LinearLayout layoutEmpty;

    private ClientAdapter clientAdapter;
    private ClientsSummaryViewModel viewModel;

    public ClientsSummaryFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_clients_summary, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshClients);
        rvClients          = view.findViewById(R.id.rvClients);
        layoutEmpty        = view.findViewById(R.id.layoutEmptyClients);

        rvClients.setLayoutManager(new LinearLayoutManager(getContext()));
        clientAdapter = new ClientAdapter();
        rvClients.setAdapter(clientAdapter);

        // --- HANDLE CLICK TO OPEN DETAILS ---
        clientAdapter.setOnClientClickListener(client -> {
            ClientDetailsFragment detailsFragment = ClientDetailsFragment.newInstance(client.getId());

            if (getParentFragmentManager() != null) {
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(((ViewGroup)getView().getParent()).getId(), detailsFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.loadClientsList());

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ClientsSummaryViewModel.class);
        observeViewModel();

        viewModel.loadClientsList();

        return view;
    }

    private void observeViewModel() {
        viewModel.getClients().observe(getViewLifecycleOwner(), list -> {
            if (list != null) {
                clientAdapter.submitList(new ArrayList<>(list));
                if (list.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvClients.setVisibility(View.GONE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvClients.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading != null && isLoading);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}