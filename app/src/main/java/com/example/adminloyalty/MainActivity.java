package com.example.adminloyalty;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.adminloyalty.fragments.DashboardFragment;
import com.example.adminloyalty.fragments.InboxFragment;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);

        // Default fragment
        if (savedInstanceState == null) {
            switchTo(new DashboardFragment(), R.id.menu_dashboard);
        }

        navView.setNavigationItemSelectedListener(item -> {
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
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void switchTo(@NonNull Fragment fragment, int menuItemIdToCheck) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        navView.setCheckedItem(menuItemIdToCheck);
    }

    // === Called by fragments to open the drawer ===
    public void openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
