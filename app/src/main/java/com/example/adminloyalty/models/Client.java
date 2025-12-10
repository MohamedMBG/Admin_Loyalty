package com.example.adminloyalty.models;

import com.google.firebase.Timestamp;

public class Client {

    private String id;
    private String name;
    private String email;
    private String clientCode;   // e.g. #88392 or matricule
    private long points;
    private double avgSpend;
    private Timestamp createdAt;

    public Client() {
        // Required empty constructor for Firestore
    }

    public Client(String id, String name, String email, String clientCode,
                  long points, double avgSpend, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.clientCode = clientCode;
        this.points = points;
        this.avgSpend = avgSpend;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getClientCode() { return clientCode; }
    public long getPoints() { return points; }
    public double getAvgSpend() { return avgSpend; }
    public Timestamp getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setClientCode(String clientCode) { this.clientCode = clientCode; }
    public void setPoints(long points) { this.points = points; }
    public void setAvgSpend(double avgSpend) { this.avgSpend = avgSpend; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
