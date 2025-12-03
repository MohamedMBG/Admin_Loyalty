package com.example.adminloyalty.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.ScanLog;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ScanLogAdapter extends RecyclerView.Adapter<ScanLogAdapter.ScanLogViewHolder> {

    private final List<ScanLog> scanLogList;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    public ScanLogAdapter(List<ScanLog> scanLogList) {
        this.scanLogList = scanLogList;
    }

    @NonNull
    @Override
    public ScanLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_log, parent, false);
        return new ScanLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanLogViewHolder holder, int position) {
        ScanLog log = scanLogList.get(position);

        // Order number
        if (log.getOrderNo() != null) {
            holder.tvOrderNo.setText("Order #" + log.getOrderNo());
        } else {
            holder.tvOrderNo.setText("Order -");
        }

        // For the moment we only have redeemedByUid, not full name
        if (log.getRedeemedByUid() != null) {
            holder.tvUserName.setText("Client: " + log.getRedeemedByUid());
        } else {
            holder.tvUserName.setText("Client: -");
        }

        // Use redeemedAt; if null fallback to createdAt
        Timestamp ts = log.getRedeemedAt() != null
                ? log.getRedeemedAt()
                : log.getCreatedAt();

        if (ts != null) {
            holder.tvTimestamp.setText(dateFormat.format(ts.toDate()));
        } else {
            holder.tvTimestamp.setText("N/A");
        }

        // Amount MAD
        holder.tvAmount.setText(String.format(Locale.getDefault(),
                "%.2f MAD", log.getAmountMAD()));

        // Cashier (we don't have it yet)
        holder.tvCashierName.setText("Cashier: -");

        // Points
        holder.tvPoints.setText("+" + log.getPoints() + " pts");
    }

    @Override
    public int getItemCount() {
        return scanLogList.size();
    }

    static class ScanLogViewHolder extends RecyclerView.ViewHolder {

        TextView tvOrderNo, tvUserName, tvTimestamp, tvAmount, tvCashierName, tvPoints;

        public ScanLogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderNo = itemView.findViewById(R.id.tvOrderNo);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvCashierName = itemView.findViewById(R.id.tvCashierName);
            tvPoints = itemView.findViewById(R.id.tvPoints);
        }
    }
}
