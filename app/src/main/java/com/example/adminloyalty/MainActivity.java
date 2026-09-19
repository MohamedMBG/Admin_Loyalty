package com.example.adminloyalty;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.fragments.DashboardFragment;
import com.example.adminloyalty.databinding.ActivityMainBinding;
import com.example.adminloyalty.utils.SystemBars;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.applyInsetPadding(binding.getRoot());

        // Load the dashboard as the default (and only) fragment
        if (savedInstanceState == null) {
            switchTo(new DashboardFragment());
        }
    }

    // Used by DashboardFragment to navigate to sub-screens
    // (ClientsSummary, ScanLogs, Promotions, Rewards, etc.)
    public void switchTo(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
