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

    // here we link the UI elements with the logic code
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

    //actions method :
    private void setupActions() {
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
    }


    // we should add the cashier name who made the redemption in the method named loadScanLogs

    private void loadScanLogs() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("earn_codes")
                .orderBy("redeemedAt", Query.Direction.DESCENDING)
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

                Double amountDouble = doc.getDouble("amountMAD");
                double amountMAD = amountDouble != null ? amountDouble : 0.0;

                Long pointsLong = doc.getLong("points");
                long points = pointsLong != null ? pointsLong : 0;

                Timestamp redeemedAt = doc.getTimestamp("redeemedAt");
                Timestamp createdAt = doc.getTimestamp("createdAt");
                String status = doc.getString("status");

                ScanLog log = new ScanLog(
                        id,
                        orderNo,
                        redeemedByUid,
                        amountMAD,
                        points,
                        redeemedAt,
                        createdAt,
                        status
                );

                scanLogList.add(log);
            }

            adapter.notifyDataSetChanged();

            int count = scanLogList.size();
            if (count == 0) {
                tvLogCount.setText("No redeemed scans yet");
            } else {
                tvLogCount.setText(
                        String.format(Locale.getDefault(),
                                "%d redeemed scans", count));
            }

        } else {
            tvLogCount.setText("Failed to load records");
        }
    }
}
