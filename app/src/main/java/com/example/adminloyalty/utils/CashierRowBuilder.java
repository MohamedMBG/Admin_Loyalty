package com.example.adminloyalty.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.example.adminloyalty.R;
import com.example.adminloyalty.fragments.DashboardFragment;

import java.util.List;

public class CashierRowBuilder {

    public static void renderCashierRows(LinearLayout container, List<DashboardFragment.CashierStats> cashierList,
                                         Context context, int maxRows) {

        // Clear previous rows
        container.removeAllViews();

        if (cashierList == null || cashierList.isEmpty()) {
            showEmptyState(container, context);
            return;
        }

        // Show top performers (limit to maxRows)
        int displayCount = Math.min(cashierList.size(), maxRows);
        for (int i = 0; i < displayCount; i++) {
            DashboardFragment.CashierStats stats = cashierList.get(i);
            CardView cardView = createCashierCard(context, stats, i + 1);
            container.addView(cardView);
        }
    }

    private static CardView createCashierCard(Context context, DashboardFragment.CashierStats stats, int rank) {
        // Inflate card layout
        CardView cardView = (CardView) LayoutInflater.from(context)
                .inflate(R.layout.item_cashier_performance, null);

        // Bind data to views
        bindCashierData(cardView, stats, rank, context);

        return cardView;
    }

    private static void bindCashierData(CardView cardView, DashboardFragment.CashierStats stats, int rank, Context context) {
        // Find views
        TextView tvRank = cardView.findViewById(R.id.tvRank);
        TextView tvCashierName = cardView.findViewById(R.id.tvCashierName);
        TextView tvCashierId = cardView.findViewById(R.id.tvCashierId);
        TextView tvScansValue = cardView.findViewById(R.id.tvScansValue);
        TextView tvRedeemsValue = cardView.findViewById(R.id.tvRedeemsValue);
        TextView tvTotalValue = cardView.findViewById(R.id.tvTotalValue);
        TextView tvPerformanceStatus = cardView.findViewById(R.id.tvPerformanceStatus);

        // Set data
        tvRank.setText(String.valueOf(rank));
        tvCashierName.setText(stats.name);
        tvCashierId.setText(formatCashierId(stats.id));
        tvScansValue.setText(String.valueOf(stats.scans));
        tvRedeemsValue.setText(String.valueOf(stats.redeems));

        int total = stats.scans + stats.redeems;
        tvTotalValue.setText(String.valueOf(total));

        // Set performance status
        PerformanceLevel level = getPerformanceLevel(rank, total);
        tvPerformanceStatus.setText(level.text);
        tvPerformanceStatus.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor(level.color)
                )
        );
    }

    private static PerformanceLevel getPerformanceLevel(int rank, int total) {
        if (rank == 1 && total > 50) {
            return new PerformanceLevel("TOP", "#10B981");
        } else if (total > 30) {
            return new PerformanceLevel("GOOD", "#3B82F6");
        } else if (total > 10) {
            return new PerformanceLevel("AVG", "#F59E0B");
        } else {
            return new PerformanceLevel("LOW", "#6B7280");
        }
    }

    private static String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "??";

        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        for (int i = 0; i < Math.min(parts.length, 2); i++) {
            if (!parts[i].isEmpty()) {
                initials.append(parts[i].charAt(0));
            }
        }

        return initials.toString().toUpperCase();
    }

    private static String formatCashierId(String id) {
        if (id == null || id.length() <= 8) return id;
        return id.substring(0, 8) + "...";
    }

    private static void showEmptyState(LinearLayout container, Context context) {
        TextView emptyText = new TextView(context);
        emptyText.setText("No cashier data available");
        emptyText.setTextColor(context.getColor(android.R.color.darker_gray));
        emptyText.setTextSize(14);
        emptyText.setPadding(0, 32, 0, 32);
        emptyText.setGravity(android.view.Gravity.CENTER);

        container.addView(emptyText);
    }

    private static class PerformanceLevel {
        String text;
        String color;

        PerformanceLevel(String text, String color) {
            this.text = text;
            this.color = color;
        }
    }
}