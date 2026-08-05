package com.example.adminloyalty;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
/**
 * Initializes application-wide settings before any activity is displayed.
 *
 * <p>French is selected on the first launch only. Later language selections made through
 * Android's App language setting are left unchanged.</p>
 */
public class AdminLoyaltyApplication extends Application {
    private static final String LANGUAGE_PREFERENCES = "language_preferences";
    private static final String KEY_LANGUAGE_INITIALIZED = "language_initialized";

    @Override
    public void onCreate() {
        super.onCreate();
        applyInitialFrenchLocale();
    }

    /**
     * Selects French for a new installation without overriding an existing language choice.
     *
     * <p>The preference prevents the application from resetting a user-selected language on
     * subsequent launches.</p>
     */
    private void applyInitialFrenchLocale() {
        SharedPreferences preferences = getSharedPreferences(LANGUAGE_PREFERENCES, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_LANGUAGE_INITIALIZED, false)) {
            return;
        }

        if (AppCompatDelegate.getApplicationLocales().isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"));
        }
        preferences.edit().putBoolean(KEY_LANGUAGE_INITIALIZED, true).apply();
    }
}
