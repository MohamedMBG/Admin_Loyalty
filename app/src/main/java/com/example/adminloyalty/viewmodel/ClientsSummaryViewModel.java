package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.ClientsSummaryRepository;
import com.example.adminloyalty.data.api.ApiErrors;
import com.example.adminloyalty.data.api.ApiResult;
import com.example.adminloyalty.di.IoExecutor;
import com.example.adminloyalty.models.Client;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ClientsSummaryViewModel extends ViewModel {

    private static final int ROSTER_LIMIT = 100;

    private final ClientsSummaryRepository repository;
    private final ExecutorService io;

    private final MutableLiveData<List<Client>> clientsInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public ClientsSummaryViewModel(ClientsSummaryRepository repository, @IoExecutor ExecutorService io) {
        this.repository = repository;
        this.io = io;
    }

    public LiveData<List<Client>> getClients() { return clientsInfo; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadClientsList() {
        loading.postValue(true);
        io.execute(() -> {
            ApiResult result = repository.listUsers(ROSTER_LIMIT);
            loading.postValue(false);

            if (!result.isOk()) {
                error.postValue(ApiErrors.message(result, "Not authorized. Admin role required.",
                        "Failed to load clients"));
                return;
            }

            List<Client> list = new ArrayList<>();
            JSONArray users = result.data != null ? result.data.optJSONArray("users") : null;
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    JSONObject o = users.optJSONObject(i);
                    if (o == null) continue;

                    String uid = o.optString("uid", null);
                    String name = o.optString("fullName", null);
                    if (name == null || name.isEmpty()) name = "Unknown Client";
                    String email = o.optString("email", null);
                    long points = o.optLong("points", 0);
                    long createdAtMs = o.optLong("createdAt", 0);
                    Timestamp createdAt = createdAtMs > 0 ? new Timestamp(new Date(createdAtMs)) : null;

                    // clientCode = uid (the old code used the users doc's uid field). avgSpend is
                    // zeroed — the backend roster carries no spend metric (see docs/MIGRATION_PROGRESS).
                    list.add(new Client(uid, name, email, uid, points, 0.0, createdAt));
                }
            }
            clientsInfo.postValue(list);
        });
    }
}
