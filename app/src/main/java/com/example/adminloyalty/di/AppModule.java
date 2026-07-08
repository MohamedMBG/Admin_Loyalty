package com.example.adminloyalty.di;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public FirebaseFirestore provideFirestore() {
        return FirebaseFirestore.getInstance();
    }

    @Provides
    @Singleton
    public com.google.firebase.auth.FirebaseAuth provideFirebaseAuth() {
        return com.google.firebase.auth.FirebaseAuth.getInstance();
    }

    @Provides
    @Singleton
    public com.example.adminloyalty.data.api.AuthInterceptor provideAuthInterceptor(
            com.google.firebase.auth.FirebaseAuth auth) {
        return new com.example.adminloyalty.data.api.AuthInterceptor(auth);
    }

    /**
     * Shared app-wide IO pool for blocking backend calls. Never shut down by callers.
     * Bounded so slow/stalled backend calls can't spawn threads without limit; excess work
     * runs on the caller thread (CallerRunsPolicy) once both pool and queue are full.
     */
    @Provides
    @Singleton
    @IoExecutor
    public ExecutorService provideIoExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cores, cores * 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
