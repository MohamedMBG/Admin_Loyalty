package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateCashierRepository {

    private final AdminApiClient api;

    @Inject
    public CreateCashierRepository(AdminApiClient api) {
        this.api = api;
    }

    /**
     * Provision a cashier via the backend. Blocking — call on a background thread. Replaces the old
     * client-side flow (a secondary FirebaseApp to create the auth user + a direct users-doc write):
     * the backend now creates the auth account, sets the {@code role: cashier} custom claim (which the
     * client SDK cannot do), and writes the profile doc. Not idempotency-keyed — email uniqueness is
     * the guard.
     */
    public ApiResult createCashier(String name, String email, String password) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("name", name);
        } catch (Exception e) {
            return ApiResult.clientError("Failed to build request");
        }
        return api.post("/admin/cashiers", body, null);
    }
}
