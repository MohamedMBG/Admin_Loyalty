package com.example.adminloyalty;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.fragments.DashboardFragment;
import com.example.adminloyalty.fragments.InboxFragment;
import com.example.adminloyalty.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Default fragment
        if (savedInstanceState == null) {
            switchTo(new DashboardFragment(), R.id.menu_dashboard);
        }

        binding.navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_dashboard) {
                switchTo(new DashboardFragment(), id);
            } else if (id == R.id.menu_inbox) {
                switchTo(new InboxFragment(), id);
            } else if (id == R.id.menu_settings) {
                // TODO: your SettingsFragment
                // switchTo(new SettingsFragment(), id);
            } else if (id == R.id.menu_logout) {
                // TODO: handle logout
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void switchTo(@NonNull Fragment fragment, int menuItemIdToCheck) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        binding.navView.setCheckedItem(menuItemIdToCheck);
    }

    // === Called by fragments to open the drawer ===
    public void openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
