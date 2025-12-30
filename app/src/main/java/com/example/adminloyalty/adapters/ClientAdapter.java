package com.example.adminloyalty.adapters;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.Client;

import java.text.DecimalFormat;
import java.util.Locale;

public class ClientAdapter extends ListAdapter<Client, ClientAdapter.ClientViewHolder> {

    private OnClientClickListener listener;

    public interface OnClientClickListener {
        void onClientClick(Client client);
    }

    public void setOnClientClickListener(OnClientClickListener listener) {
        this.listener = listener;
    }

    public ClientAdapter() {
        super(new DiffUtil.ItemCallback<Client>() {
            @Override
            public boolean areItemsTheSame(@NonNull Client oldItem, @NonNull Client newItem) {
                return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Client oldItem, @NonNull Client newItem) {
                return oldItem.getPoints() == newItem.getPoints()
                        && Double.compare(oldItem.getAvgSpend(), newItem.getAvgSpend()) == 0
                        && safeEquals(oldItem.getName(), newItem.getName())
                        && safeEquals(oldItem.getEmail(), newItem.getEmail());
            }
        });
    }

    @NonNull
    @Override
    public ClientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_client, parent, false);
        return new ClientViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ClientViewHolder holder, int position) {
        Client c = getItem(position);

        // Name & email
        String name = c.getName() != null ? c.getName() : "-";
        String email = c.getEmail() != null ? c.getEmail() : "-";

        holder.tvClientName.setText(name);
        holder.tvClientEmail.setText(email);


        // Points
        holder.tvPoints.setText(formatPoints(c.getPoints()));

        // Avg spend
        holder.tvAvgSpend.setText(formatCurrency(c.getAvgSpend()));

        // Click
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClientClick(c);
        });
    }

    static class ClientViewHolder extends RecyclerView.ViewHolder {

        TextView tvClientName, tvClientEmail, tvPoints, tvAvgSpend;

        public ClientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvClientName = itemView.findViewById(R.id.tvClientName);
            tvClientEmail= itemView.findViewById(R.id.tvClientEmail);
            tvPoints     = itemView.findViewById(R.id.tvPoints);
            tvAvgSpend   = itemView.findViewById(R.id.tvAvgSpend);
        }
    }

    // ----- Helpers -----


    private String formatPoints(long points) {
        // 3450 => "3 450"
        return String.format(Locale.getDefault(), "%,d", points).replace(',', ' ');
    }

    private String formatCurrency(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(value);
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
