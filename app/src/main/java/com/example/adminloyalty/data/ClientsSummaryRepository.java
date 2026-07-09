package com.example.adminloyalty.data;

import com.example.adminloyalty.data.api.AdminApiClient;
import com.example.adminloyalty.data.api.ApiResult;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientsSummaryRepository {

    private final AdminApiClient api;

    @Inject
    public ClientsSummaryRepository(AdminApiClient api) {
        this.api = api;
    }

    /**
     * The admin client roster via the backend. Blocking — call on a background thread.
     * Replaces the direct `users` collection read (rules-denied) plus the per-row `activities`
     * query that summed points: the roster already carries points/visits. Returns
     * {@code {users:[{uid,fullName,email,phone,points,visits,createdAt}]}}.
     */
    public ApiResult listUsers(int limit) {
        return api.get("/admin/users?limit=" + limit);
    }
}
