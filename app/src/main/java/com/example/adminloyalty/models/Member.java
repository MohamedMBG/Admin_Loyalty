package com.example.adminloyalty.models;

public class Member {

    private String id;        // Firestore document id
    private String uid;       // uid field in document
    private String fullName;
    private String email;
    private String birthday;  // "2003-11-02"
    private Long points;
    private Long visits;
    private Boolean isVerified;

    public Member() {
        // Needed by Firestore
    }

    // ---- setters/getters ----

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUid() {
        return uid != null ? uid : "";
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName != null ? fullName : "";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBirthday() {
        return birthday != null ? birthday : "";
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public Long getPoints() {
        return points != null ? points : 0L;
    }

    public void setPoints(Long points) {
        this.points = points;
    }

    public Long getVisits() {
        return visits != null ? visits : 0L;
    }

    public void setVisits(Long visits) {
        this.visits = visits;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(Boolean verified) {
        isVerified = verified;
    }
}
