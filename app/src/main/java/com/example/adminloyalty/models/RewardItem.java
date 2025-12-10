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
    public boolean isVisible() { return isVisible; }
}
