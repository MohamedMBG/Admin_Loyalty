package com.example.adminloyalty.models;

import org.json.JSONObject;

/**
 * A user summary from {@code GET /api/v1/admin/users/search}.
 * Mirrors the backend {@code UserSearchResponse.UserSummary} — the only fields the backend
 * exposes to admins (Firestore rules deny direct reads of other users' profiles). Notably
 * there is NO full name / gender / birthday / address here.
 */
public class AdminUser {
    public final String uid;
    public final String email;
    public final String phone;
    public final int points;
    public final long visits;

    public AdminUser(String uid, String email, String phone, int points, long visits) {
        this.uid = uid;
        this.email = email;
        this.phone = phone;
        this.points = points;
        this.visits = visits;
    }

    public static AdminUser fromJson(JSONObject o) {
        return new AdminUser(
                o.optString("uid", null),
                o.optString("email", null),
                o.optString("phone", null),
                o.optInt("points", 0),
                o.optLong("visits", 0));
    }

    /** Best available display label — backend gives no full name. */
    public String displayName() {
        if (email != null && !email.isEmpty()) return email;
        if (phone != null && !phone.isEmpty()) return phone;
        return uid != null ? uid : "Unknown User";
    }
}
