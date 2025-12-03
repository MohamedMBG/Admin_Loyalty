package com.example.adminloyalty.models;

import com.google.firebase.Timestamp;

public class ScanLog {

    private String id;
    private String orderNo;
    private String redeemedByUid;
    private double amountMAD;
    private long points;
    private Timestamp redeemedAt;
    private Timestamp createdAt;
    private String status;

    public ScanLog() {
        // Firestore needs empty constructor
    }

    public ScanLog(String id, String orderNo, String redeemedByUid,
                   double amountMAD, long points,
                   Timestamp redeemedAt, Timestamp createdAt,
                   String status) {
        this.id = id;
        this.orderNo = orderNo;
        this.redeemedByUid = redeemedByUid;
        this.amountMAD = amountMAD;
        this.points = points;
        this.redeemedAt = redeemedAt;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getRedeemedByUid() { return redeemedByUid; }
    public double getAmountMAD() { return amountMAD; }
    public long getPoints() { return points; }
    public Timestamp getRedeemedAt() { return redeemedAt; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
}
