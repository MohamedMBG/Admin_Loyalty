package com.example.adminloyalty.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.ScanLog;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ScanLogAdapter extends ListAdapter<ScanLog, ScanLogAdapter.ScanLogViewHolder> {

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    public ScanLogAdapter() {
        super(new DiffUtil.ItemCallback<ScanLog>() {
            @Override
            public boolean areItemsTheSame(@NonNull ScanLog oldItem, @NonNull ScanLog newItem) {
                return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ScanLog oldItem, @NonNull ScanLog newItem) {
                return oldItem.getPoints() == newItem.getPoints()
                        && Double.compare(oldItem.getAmountMAD(), newItem.getAmountMAD()) == 0
                        && safeEquals(oldItem.getOrderNo(), newItem.getOrderNo())
                        && safeEquals(oldItem.getClientName(), newItem.getClientName())
                        && safeEquals(oldItem.getCashierName(), newItem.getCashierName())
                        && safeTimestampEquals(oldItem.getRedeemedAt(), newItem.getRedeemedAt())
                        && safeTimestampEquals(oldItem.getCreatedAt(), newItem.getCreatedAt());
            }
        });
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
        ScanLog log = getItem(position);

        if (log.getOrderNo() != null) {
            holder.tvOrderNo.setText("Order #" + log.getOrderNo());
        } else {
            holder.tvOrderNo.setText("Order -");
        }

        if (log.getRedeemedByUid() != null) {
            holder.tvUserName.setText("Client: " + log.getClientName());
        } else {
            holder.tvUserName.setText("Client: -");
        }

        Timestamp ts = log.getRedeemedAt() != null
                ? log.getRedeemedAt()
                : log.getCreatedAt();

        if (ts != null) {
            holder.tvTimestamp.setText(dateFormat.format(ts.toDate()));
        } else {
            holder.tvTimestamp.setText("N/A");
        }

        holder.tvAmount.setText(String.format(Locale.getDefault(),
                "%.2f MAD", log.getAmountMAD()));

        holder.tvCashierName.setText("Cashier: " + log.getCashierName());
        holder.tvPoints.setText("+" + log.getPoints() + " pts");
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

    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static boolean safeTimestampEquals(Timestamp a, Timestamp b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.toDate().equals(b.toDate());
    }
}
