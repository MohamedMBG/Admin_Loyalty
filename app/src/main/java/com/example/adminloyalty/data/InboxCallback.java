package com.example.adminloyalty.data;

public interface InboxCallback<T> {
    void onSuccess(T result);
    void onError(String message);
}
