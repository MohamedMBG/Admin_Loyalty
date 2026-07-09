package com.example.adminloyalty.models;

import org.json.JSONObject;

/**
 * One user's full profile from {@code GET /api/v1/admin/users/{uid}} — the client-details header.
 * Mirrors the backend {@code UserDetailResponse}. {@code lastEarnAt} is the closest backend proxy
 * for "last visit" (epoch millis; 0 when absent); {@code createdAt} is signup epoch millis.
 */
public class AdminUserDetail {
    public final String uid;
    public final String fullName;
    public final String email;
    public final String phone;
    public final String gender;
    public final String address;
    public final String birthday;
    public final long points;
    public final long visits;
    public final long createdAt;
    public final long lastEarnAt;

    public AdminUserDetail(String uid, String fullName, String email, String phone, String gender,
                           String address, String birthday, long points, long visits,
                           long createdAt, long lastEarnAt) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.gender = gender;
        this.address = address;
        this.birthday = birthday;
        this.points = points;
        this.visits = visits;
        this.createdAt = createdAt;
        this.lastEarnAt = lastEarnAt;
    }

    public static AdminUserDetail fromJson(JSONObject o) {
        return new AdminUserDetail(
                o.optString("uid", null),
                o.optString("fullName", null),
                o.optString("email", null),
                o.optString("phone", null),
                o.optString("gender", null),
                o.optString("address", null),
                o.optString("birthday", null),
                o.optLong("points", 0),
                o.optLong("visits", 0),
                o.optLong("createdAt", 0),
                o.optLong("lastEarnAt", 0));
    }
}
