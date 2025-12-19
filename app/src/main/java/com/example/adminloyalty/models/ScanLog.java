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
    private String clientName;
    private String cashierName;

    public ScanLog() {
        // Firestore needs empty constructor
    }

    public ScanLog(String id, String orderNo, String redeemedByUid, String clientName, String cashierName,
                   double amountMAD, long points, Timestamp redeemedAt, Timestamp createdAt, String status) {
        this.id = id;
        this.orderNo = orderNo;
        this.redeemedByUid = redeemedByUid;
        this.amountMAD = amountMAD;
        this.points = points;
        this.redeemedAt = redeemedAt;
        this.createdAt = createdAt;
        this.status = status;
        this.cashierName = cashierName;
        this.clientName = clientName;
    }

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getRedeemedByUid() { return redeemedByUid; }
    public double getAmountMAD() { return amountMAD; }
    public long getPoints() { return points; }
    public Timestamp getRedeemedAt() { return redeemedAt; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public String getCashierName() {
        return cashierName;
    }

    public void setCashierName(String cashierName) {
        this.cashierName = cashierName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
}
