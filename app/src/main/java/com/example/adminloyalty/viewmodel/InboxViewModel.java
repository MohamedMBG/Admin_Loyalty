package com.example.adminloyalty.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.adminloyalty.data.InboxRepository;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class InboxViewModel extends ViewModel {

    private final InboxRepository repository;

    private final MutableLiveData<Integer> recipientCount = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSending = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPreviewing = new MutableLiveData<>(false);
    private final MutableLiveData<String> sendResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public InboxViewModel(InboxRepository repository) {
        this.repository = repository;
    }

    public LiveData<Integer> getRecipientCount() { return recipientCount; }
    public LiveData<Boolean> getIsSending() { return isSending; }
    public LiveData<Boolean> getIsPreviewing() { return isPreviewing; }
    public LiveData<String> getSendResult() { return sendResult; }
    public LiveData<String> getError() { return error; }

    public void previewRecipientCount(JSONObject filters) {
        isPreviewing.setValue(true);
        repository.fetchRecipientCount(filters, new InboxRepository.InboxCallback<Integer>() {
            @Override public void onSuccess(Integer count) {
                recipientCount.postValue(count);
                isPreviewing.postValue(false);
            }
            @Override public void onError(String message) {
                error.postValue(message);
                isPreviewing.postValue(false);
            }
        });
    }

    public void sendPush(String title, String message, JSONObject filters) {
        isSending.setValue(true);
        repository.sendPush(title, message, filters, new InboxRepository.InboxCallback<String>() {
            @Override public void onSuccess(String result) {
                sendResult.postValue(result);
                isSending.postValue(false);
            }
            @Override public void onError(String message) {
                error.postValue(message);
                isSending.postValue(false);
            }
        });
    }

    public void clearSendResult() { sendResult.setValue(null); }
    public void clearError() { error.setValue(null); }
}
