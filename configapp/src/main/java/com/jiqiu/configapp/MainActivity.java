package com.jiqiu.configapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements SettingsFragment.OnSettingsChangeListener {

    private Runnable pendingRootUi;
    private BottomNavigationView bottomNavigationView;
    private AppListFragment appListFragment;
    private SettingsFragment settingsFragment;
    private SoManagerFragment soManagerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        bottomNavigationView.setVisibility(android.view.View.GONE);
        android.widget.ProgressBar checking = new android.widget.ProgressBar(this);
        checking.setContentDescription(getString(R.string.root_check));
        findViewById(R.id.nav_host_fragment).setVisibility(android.view.View.GONE);
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams progressParams =
            new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(96, 96);
        progressParams.topToTop = progressParams.bottomToBottom = 0;
        progressParams.startToStart = progressParams.endToEnd = 0;
        ((android.view.ViewGroup) findViewById(R.id.main)).addView(checking, progressParams);
        com.topjohnwu.superuser.Shell.getShell(shell -> {
            if (isFinishing() || isDestroyed()) return;
            pendingRootUi = () -> {
                ((android.view.ViewGroup) findViewById(R.id.main)).removeView(checking);
                if (!shell.isRoot()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.root_required).setMessage(R.string.root_message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.close, (dialog, which) -> finishAndRemoveTask())
                        .show();
                    return;
                }
                findViewById(R.id.nav_host_fragment).setVisibility(android.view.View.VISIBLE);
                bottomNavigationView.setVisibility(android.view.View.VISIBLE);
                appListFragment = (AppListFragment) getSupportFragmentManager().findFragmentByTag("apps");
                soManagerFragment = (SoManagerFragment) getSupportFragmentManager().findFragmentByTag("library");
                settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settings");
                setupBottomNavigation();
                int selected = getPreferences(MODE_PRIVATE).getInt("selected_tab", R.id.navigation_apps);
                bottomNavigationView.setSelectedItemId(selected);
            };
            renderRootResult();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        renderRootResult();
    }

    private void renderRootResult() {
        if (pendingRootUi != null && !getSupportFragmentManager().isStateSaved()) {
            Runnable update = pendingRootUi;
            pendingRootUi = null;
            update.run();
        }
    }

    private void initViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            getPreferences(MODE_PRIVATE).edit().putInt("selected_tab", itemId).apply();
            if (itemId == R.id.navigation_apps) {
                showAppListFragment();
                return true;
            } else if (itemId == R.id.navigation_so_manager) {
                showSoManagerFragment();
                return true;
            } else if (itemId == R.id.navigation_settings) {
                showSettingsFragment();
                return true;
            }
            return false;
        });
    }

    private void showAppListFragment() {
        if (appListFragment == null) {
            appListFragment = new AppListFragment();
        }
        showFragment(appListFragment, "apps");
    }

    private void showSoManagerFragment() {
        if (soManagerFragment == null) {
            soManagerFragment = new SoManagerFragment();
        }
        showFragment(soManagerFragment, "library");
    }

    private void showSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = new SettingsFragment();
            settingsFragment.setOnSettingsChangeListener(this);
        }
        settingsFragment.setOnSettingsChangeListener(this);
        showFragment(settingsFragment, "settings");
    }

    private void showFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        for (Fragment existing : fragmentManager.getFragments()) {
            if (existing != fragment && existing.isAdded()) transaction.hide(existing);
        }
        if (fragment.isAdded()) transaction.show(fragment);
        else transaction.add(R.id.nav_host_fragment, fragment, tag);
        transaction.commitNow();
    }

    @Override
    public void onHideSystemAppsChanged(boolean hideSystemApps) {
        // 当设置改变时，通知应用列表Fragment更新过滤
        if (appListFragment != null) {
            appListFragment.setHideSystemApps(hideSystemApps);
        }
    }
}