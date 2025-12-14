package com.example.adminloyalty.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class Promotion {
    @Exclude private String id;
    private String title;
    private String criteria;
    private String value;
    private boolean active;
    private int priority;      // NEW: Higher number = Higher priority
    private Timestamp startDate;
    private Timestamp endDate;

    public Promotion() {}

    public Promotion(String title, String criteria, String value, boolean active, int priority, Timestamp startDate, Timestamp endDate) {
        this.title = title;
        this.criteria = criteria;
        this.value = value;
        this.active = active;
        this.priority = priority;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public String getCriteria() { return criteria; }
    public String getValue() { return value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp startDate) { this.startDate = startDate; }
    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp endDate) { this.endDate = endDate; }
}