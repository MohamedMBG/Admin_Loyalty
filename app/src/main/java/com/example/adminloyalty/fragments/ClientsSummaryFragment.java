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
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import okhttp3.internal.concurrent.Task;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

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

        db.collection("users")
                .orderBy("points", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(query -> {

                    List<Client> list = new ArrayList<>();
                    List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : query) {
                        try {
                            String id = doc.getId();

                            String name = doc.getString("fullName");
                            if (name == null || name.isEmpty()) name = "Unknown Client";

                            String email = doc.getString("email");
                            String clientCode = doc.getString("uid");
                            Timestamp createdAt = doc.getTimestamp("createdAt");

                            // Create client now with placeholders
                            Client c = new Client(id, name, email, clientCode, 0L, 0.0, createdAt);
                            list.add(c);

                            // FIX 3: Assign the Firestore operation to a variable 't'
                            com.google.android.gms.tasks.Task<QuerySnapshot> t = db.collection("users")
                                    .document(id)
                                    .collection("activities")
                                    .whereEqualTo("type", "earn")
                                    .get()
                                    .addOnSuccessListener(activitiesSnap -> {
                                        long totalPoints = 0L;
                                        long visits = 0L;

                                        for (QueryDocumentSnapshot activityDoc : activitiesSnap) {
                                            Long pts = activityDoc.getLong("points");
                                            if (pts != null) {
                                                totalPoints += pts;
                                                visits++;
                                            }
                                        }

                                        double avg = (visits > 0) ? (double) totalPoints / visits : 0.0;

                                        // Update the object in memory
                                        c.setPoints(totalPoints);
                                        c.setAvgSpend(avg);
                                    });

                            tasks.add(t);

                        } catch (Exception e) {
                            Log.e("ClientLoad", "Error parsing client: " + doc.getId(), e);
                        }
                    }

                    Tasks.whenAllComplete(tasks)
                            .addOnSuccessListener(done -> {
                                swipeRefreshLayout.setRefreshing(false);
                                updateClientListUI(list);
                            })
                            .addOnFailureListener(e -> {
                                swipeRefreshLayout.setRefreshing(false);
                                Log.e("ClientLoad", "Activities load error", e);
                                updateClientListUI(list); // Show whatever we managed to load
                            });

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