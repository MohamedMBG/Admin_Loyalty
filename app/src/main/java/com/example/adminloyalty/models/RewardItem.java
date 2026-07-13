package com.example.adminloyalty.models;

import com.google.firebase.firestore.Exclude;

public class RewardItem {
    @Exclude
    private String id;
    private String name;
    private String category;
    private int costPoints;
    private boolean isVisible;

    public RewardItem() {}

    public RewardItem(String name, String category, int costPoints) {
        this.name = name;
        this.category = category;
        this.costPoints = costPoints;
        this.isVisible = true;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getCostPoints() { return costPoints; }
    public void setCostPoints(int costPoints) { this.costPoints = costPoints; }
    public boolean isVisible() { return isVisible; }
    public void setVisible(boolean visible) { this.isVisible = visible; }

    /**
     * Map a rewards_catalog document, tolerating both schemas: legacy admin-app docs
     * ({@code costPoints}/{@code visible}) and backend-written docs ({@code cost}/{@code active}).
     * Returns null instead of throwing on a malformed doc so one bad entry can't crash a screen.
     */
    @Exclude
    public static RewardItem fromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        try {
            RewardItem item = doc.toObject(RewardItem.class);
            if (item == null) return null;
            item.setId(doc.getId());
            if (item.costPoints == 0) {
                Long cost = doc.getLong("cost");
                if (cost != null) item.costPoints = cost.intValue();
            }
            if (!item.isVisible) {
                Boolean active = doc.getBoolean("active");
                if (active != null) item.isVisible = active;
            }
            return item;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
