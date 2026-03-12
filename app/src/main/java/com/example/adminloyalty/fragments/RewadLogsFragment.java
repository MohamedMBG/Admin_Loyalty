package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.viewmodel.RewardLogsViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RewadLogsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvTotalCost, tvRecordCount;
    private ImageView btnBack, btnExport;

    private RedemptionAdapter adapter;
    private List<DocumentSnapshot> logList = new ArrayList<>();
    private RewardLogsViewModel viewModel;

    private static final double POINT_TO_MAD = 0.2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_rewad_logs, container, false);

        initViews(v);
        setupRecyclerView();

        viewModel = new ViewModelProvider(this).get(RewardLogsViewModel.class);
        observeViewModel();

        viewModel.loadRedemptions();

        return v;
    }

    private void initViews(View v) {
        recyclerView = v.findViewById(R.id.recyclerViewRedemptions);
        progressBar = v.findViewById(R.id.progressBar);
        tvTotalCost = v.findViewById(R.id.tvTotalCost);
        tvRecordCount = v.findViewById(R.id.tvRecordCount);
        btnBack = v.findViewById(R.id.btnBack);
        btnExport = v.findViewById(R.id.btnExport);

        btnBack.setOnClickListener(view -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        btnExport.setOnClickListener(view -> exportToExcel());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RedemptionAdapter(logList);
        recyclerView.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getRedemptions().observe(getViewLifecycleOwner(), redemptions -> {
            if (redemptions != null) {
                logList.clear();
                logList.addAll(redemptions);
                calculateTotal(logList);
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void calculateTotal(List<DocumentSnapshot> docs) {
        double totalMad = 0;

        for (DocumentSnapshot doc : docs) {
            Long points = doc.getLong("costPoints");
            if (points != null) {
                totalMad += points * POINT_TO_MAD;
            }
        }

        tvTotalCost.setText(String.format(Locale.US, "%.2f MAD", totalMad));
        tvRecordCount.setText(docs.size() + " records");
    }

    private void exportToExcel() {
        if (logList == null || logList.isEmpty()) {
            Toast.makeText(getContext(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAdded() || getContext() == null) return;

        try {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "exports");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String fileName = "reward_logs_" + timeStamp + ".csv";
            java.io.File file = new java.io.File(dir, fileName);

            java.io.FileWriter writer = new java.io.FileWriter(file);

            writer.append("Item Name,Client Name,Cost MAD,Cost Points,Cashier Name,Created At\n");

            java.text.SimpleDateFormat sdfExport =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

            for (DocumentSnapshot doc : logList) {
                String item = doc.getString("itemName");
                String client = doc.getString("userName");
                Double costMad = doc.getDouble("costMAD");
                Long costPoints = doc.getLong("costPoints");
                String cashier = doc.getString("cashierName");
                Timestamp ts = doc.getTimestamp("createdAt");

                String dateStr = ts != null ? sdfExport.format(ts.toDate()) : "";

                writer.append(escapeCsv(item));
                writer.append(",");
                writer.append(escapeCsv(client));
                writer.append(",");
                writer.append(costMad != null ? String.format(java.util.Locale.US, "%.2f", costMad) : "");
                writer.append(",");
                writer.append(costPoints != null ? String.valueOf(costPoints) : "");
                writer.append(",");
                writer.append(escapeCsv(cashier));
                writer.append(",");
                writer.append(escapeCsv(dateStr));
                writer.append("\n");
            }

            writer.flush();
            writer.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(android.content.Intent.createChooser(intent, "Share export"));

            Toast.makeText(getContext(), "Exported to: " + file.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String v = value;
        if (v.contains("\"")) {
            v = v.replace("\"", "\"\"");
        }
        if (v.contains(",") || v.contains("\n")) {
            v = "\"" + v + "\"";
        }
        return v;
    }

    private class RedemptionAdapter extends RecyclerView.Adapter<RedemptionAdapter.ViewHolder> {

        private final List<DocumentSnapshot> items;
        private final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

        public RedemptionAdapter(List<DocumentSnapshot> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_redemption_log, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = items.get(position);

            String item = doc.getString("itemName");
            holder.tvItemName.setText(item != null ? item : "Unknown Item");

            String client = doc.getString("userName");
            holder.tvCustomerName.setText(client != null ? "Client: " + client : "Client: Unknown");

            Long points = doc.getLong("costPoints");
            double costMad = 0;
            if (points != null) {
                costMad = points * POINT_TO_MAD;
            }
            holder.tvCostMad.setText(String.format(Locale.US, "-%.2f MAD", costMad));

            holder.tvCostPoints.setText(points != null ? "(" + points + " pts)" : "(0 pts)");

            String cashier = doc.getString("cashierName");
            holder.tvCashierName.setText(cashier != null ? "Processed by: " + cashier : "By: System");

            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null) {
                holder.tvTimestamp.setText(sdf.format(ts.toDate()));
            } else {
                holder.tvTimestamp.setText("Date Unknown");
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName, tvCustomerName, tvCostMad, tvCostPoints, tvCashierName, tvTimestamp;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
                tvCostMad = itemView.findViewById(R.id.tvCostMad);
                tvCostPoints = itemView.findViewById(R.id.tvCostPoints);
                tvCashierName = itemView.findViewById(R.id.tvCashierName);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            }
        }
    }
}