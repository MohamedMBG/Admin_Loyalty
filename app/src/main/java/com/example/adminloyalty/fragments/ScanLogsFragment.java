package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.adapters.ScanLogAdapter;
import com.example.adminloyalty.models.ScanLog;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScanLogsFragment extends Fragment {

    private RecyclerView recyclerViewLogs;
    private ProgressBar progressBar;
    private TextView tvLogCount;
    private ImageView btnBack;

    private ScanLogAdapter adapter;
    private final List<ScanLog> scanLogList = new ArrayList<>();
    private FirebaseFirestore db;

    public ScanLogsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_logs, container, false);

        initializeViews(view);
        setupActions();
        loadScanLogs();

        return view;
    }

    private void initializeViews(View view) {
        recyclerViewLogs = view.findViewById(R.id.recyclerViewLogs);
        progressBar = view.findViewById(R.id.progressBar);
        tvLogCount = view.findViewById(R.id.tvLogCount);
        btnBack = view.findViewById(R.id.btnBack);

        db = FirebaseFirestore.getInstance();

        adapter = new ScanLogAdapter(scanLogList);
        recyclerViewLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewLogs.setAdapter(adapter);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    private void loadScanLogs() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("earn_codes")
                .orderBy("redeemedAt", Query.Direction.DESCENDING)
                .limit(100) // Good practice to limit
                .get()
                .addOnCompleteListener(this::onLogsLoaded);
    }

    private void onLogsLoaded(@NonNull Task<QuerySnapshot> task) {
        progressBar.setVisibility(View.GONE);

        if (task.isSuccessful() && task.getResult() != null) {
            scanLogList.clear();

            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                String id = doc.getId();
                String orderNo = doc.getString("orderNo");
                String redeemedByUid = doc.getString("redeemedByUid");

                // FIX: Ensure we are getting the cashierName from the document
                // If it's null in DB, fallback to "Unknown"
                String cashierName = doc.getString("cashierName");
                if (cashierName == null) cashierName = "Unknown Cashier";

                Double amountDouble = doc.getDouble("amountMAD");
                double amountMAD = amountDouble != null ? amountDouble : 0.0;

                Long pointsLong = doc.getLong("points");
                long points = pointsLong != null ? pointsLong : 0;

                Timestamp redeemedAt = doc.getTimestamp("redeemedAt");
                Timestamp createdAt = doc.getTimestamp("createdAt");
                String status = doc.getString("status");

                // Initialize with "Loading..." or UID for client name
                String clientPlaceholder = "Client ID: " + (redeemedByUid != null ? redeemedByUid.substring(0, Math.min(redeemedByUid.length(), 6)) : "Unknown");

                ScanLog log = new ScanLog(
                        id,
                        orderNo,
                        redeemedByUid,
                        clientPlaceholder, // Temp name
                        cashierName,       // Actual cashier name
                        amountMAD,
                        points,
                        redeemedAt,
                        createdAt,
                        status
                );

                scanLogList.add(log);

                // Async fetch real client name
                if (redeemedByUid != null) {
                    fetchClientName(redeemedByUid, log);
                }
            }

            adapter.notifyDataSetChanged();
            updateCountText();

        } else {
            tvLogCount.setText("Failed to load records");
        }
    }

    private void fetchClientName(String uid, ScanLog log) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("fullName");
                        if (name != null && !name.isEmpty()) {
                            log.setClientName(name);
                            // Notify adapter to update this specific item
                            // finding index is inefficient in loop, but okay for small lists.
                            // Better: notifyDataSetChanged() or use DiffUtil
                            int index = scanLogList.indexOf(log);
                            if (index != -1) {
                                adapter.notifyItemChanged(index);
                            }
                        }
                    }
                });
    }

    private void updateCountText() {
        int count = scanLogList.size();
        if (count == 0) {
            tvLogCount.setText("No redeemed scans yet");
        } else {
            tvLogCount.setText(String.format(Locale.getDefault(), "%d redeemed scans", count));
        }
    }
}