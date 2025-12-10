package com.example.adminloyalty.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.example.adminloyalty.R;
import com.example.adminloyalty.models.RewardItem;

import java.util.ArrayList;
import java.util.List;

public class RewardAdminAdapter extends RecyclerView.Adapter<RewardAdminAdapter.ViewHolder> {

    private List<RewardItem> items = new ArrayList<>();
    private OnRewardActionListener listener;

    public interface OnRewardActionListener {
        void onEdit(RewardItem item);
        void onDelete(RewardItem item);
    }

    public void setListener(OnRewardActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<RewardItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.gift_item_layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RewardItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvCategory.setText("Family: " + item.getCategory());
        holder.tvCost.setText(item.getCostPoints() + " pts");

        // Simple initial for category icon
        String initial = item.getCategory().isEmpty() ? "?" : item.getCategory().substring(0,1).toUpperCase();
        holder.tvInitial.setText(initial);

        // Edit Action (Clicking the card)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(item);
        });

        // Delete Action
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCategory, tvCost, tvInitial;
        ImageView btnDelete;

        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvRewardName);
            tvCategory = v.findViewById(R.id.tvRewardCategory);
            tvCost = v.findViewById(R.id.tvRewardCost);
            tvInitial = v.findViewById(R.id.tvCategoryInitial);
            btnDelete = v.findViewById(R.id.btnDeleteReward);
        }
    }
}