package com.example.adminloyalty.di;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/** Qualifies the shared app-wide IO {@link java.util.concurrent.ExecutorService}. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface IoExecutor {
}
