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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.adapters.ClientAdapter;
import com.example.adminloyalty.models.Client;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ClientsSummaryFragment extends Fragment {
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvClients;
    private LinearLayout layoutEmpty;

    private ClientAdapter clientAdapter;
    private FirebaseFirestore db;

    public ClientsSummaryFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_clients_summary, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshClients);
        rvClients          = view.findViewById(R.id.rvClients);
        layoutEmpty        = view.findViewById(R.id.layoutEmptyClients);

        db = FirebaseFirestore.getInstance();

        rvClients.setLayoutManager(new LinearLayoutManager(getContext()));
        clientAdapter = new ClientAdapter();
        rvClients.setAdapter(clientAdapter);

        // --- HANDLE CLICK TO OPEN DETAILS ---
        clientAdapter.setOnClientClickListener(client -> {
            // Open the ClientDetailsFragment passing the ID
            ClientDetailsFragment detailsFragment = ClientDetailsFragment.newInstance(client.getId());

            // Assuming your container ID is nav_host_fragment or fragment_container.
            // Use the view ID of the parent container of this fragment.
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(((ViewGroup)getView().getParent()).getId(), detailsFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        swipeRefreshLayout.setOnRefreshListener(this::loadClients);
        swipeRefreshLayout.setRefreshing(true);
        loadClients();

        return view;
    }


// Inside your Fragment/Activity class

    private void loadClients() {
        swipeRefreshLayout.setRefreshing(true);

        // OPTIMIZATION 1: Order by 'points' to show VIPs first
        // OPTIMIZATION 2: Limit to 100 for performance
        db.collection("users")
                .orderBy("points", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(query -> {
                    List<Client> list = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : query) {
                        try {
                            String id = doc.getId();

                            // 1. NAME & ID SAFETY
                            String name = doc.getString("fullName");
                            if (name == null || name.isEmpty()) {
                                name = "Unknown Client";
                            }

                            String email = doc.getString("email");
                            String clientCode = doc.getString("uid");

                            // 2. METRICS PARSING
                            // Handle Points
                            Long pointsVal = doc.getLong("points");
                            long points = pointsVal != null ? pointsVal : 0L;

                            // Handle Visits
                            Long visitsVal = doc.getLong("visits");
                            long visits = visitsVal != null ? visitsVal : 0L;

                            // 3. APP-SIDE CALCULATION: Average Spend
                            // Formula: Total Value (Points) / Total Visits
                            // We check for visits > 0 to avoid Division By Zero crash
                            double avg = 0.0;
                            if (visits > 0) {
                                avg = (double) points / visits;
                            }

                            Timestamp createdAt = doc.getTimestamp("createdAt");

                            // Create the object
                            Client c = new Client(id, name, email, clientCode, points, avg, createdAt);
                            list.add(c);

                        } catch (Exception e) {
                            Log.e("ClientLoad", "Error parsing client: " + doc.getId(), e);
                        }
                    }

                    updateClientListUI(list);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Log.e("ClientLoad", "Firestore Error", e);

                    String errorMsg = "Failed to load clients.";
                    if (e instanceof FirebaseFirestoreException) {
                        errorMsg += " (Check Firestore Indexes)";
                    }
                    if (getContext() != null) {
                        Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateClientListUI(List<Client> list) {
        clientAdapter.setClients(list);
        swipeRefreshLayout.setRefreshing(false);

        if (list.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvClients.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvClients.setVisibility(View.VISIBLE);
        }
    }
}