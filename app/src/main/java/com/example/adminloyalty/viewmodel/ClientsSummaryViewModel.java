package com.example.adminloyalty.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.ClientsSummaryRepository;
import com.example.adminloyalty.models.Client;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ClientsSummaryViewModel extends ViewModel {

    private final ClientsSummaryRepository repository;

    private final MutableLiveData<List<Client>> clientsInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public ClientsSummaryViewModel(ClientsSummaryRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<Client>> getClients() { return clientsInfo; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadClientsList() {
        loading.postValue(true);
        repository.getClients()
                .addOnSuccessListener(query -> {
                    List<Client> list = new ArrayList<>();
                    List<Task<QuerySnapshot>> tasks = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : query) {
                        try {
                            String id = doc.getId();
                            String name = doc.getString("fullName");
                            if (name == null || name.isEmpty()) name = "Unknown Client";

                            String email = doc.getString("email");
                            String clientCode = doc.getString("uid");
                            Timestamp createdAt = doc.getTimestamp("createdAt");

                            Client c = new Client(id, name, email, clientCode, 0L, 0.0, createdAt);
                            list.add(c);

                            Task<QuerySnapshot> t = repository.getClientEarnActivities(id)
                                    .addOnSuccessListener(activitiesSnap -> {
                                        long totalPoints = 0L;
                                        long visits = 0L;

                                        for (QueryDocumentSnapshot activityDoc : activitiesSnap) {
                                            Long pts = activityDoc.getLong("points");
                                            if (pts != null) {
                                                totalPoints += pts;
                                                visits++;
                                            }
                                        }

                                        double avg = (visits > 0) ? (double) totalPoints / visits : 0.0;
                                        c.setPoints(totalPoints);
                                        c.setAvgSpend(avg);
                                    });
                            tasks.add(t);

                        } catch (Exception e) {
                            Log.e("ClientsSummaryVM", "Error parsing client: " + doc.getId(), e);
                        }
                    }

                    Tasks.whenAllComplete(tasks)
                            .addOnSuccessListener(done -> {
                                clientsInfo.postValue(list);
                                loading.postValue(false);
                            })
                            .addOnFailureListener(e -> {
                                Log.e("ClientsSummaryVM", "Activities load error", e);
                                clientsInfo.postValue(list);
                                loading.postValue(false);
                            });

                })
                .addOnFailureListener(e -> {
                    error.postValue("Failed to load clients: " + e.getMessage());
                    loading.postValue(false);
                });
    }
}
