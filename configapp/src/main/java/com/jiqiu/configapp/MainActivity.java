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

    private android.widget.ProgressBar checking;
    private androidx.appcompat.app.AlertDialog rootDialog;
    private boolean rootGranted;
    private boolean checkingRoot;
    private int selectedTab = R.id.navigation_so_manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Restored fragments must not run root commands before a new su session exists.
        super.onCreate(RootShell.ready() ? savedInstanceState : null);
        if (savedInstanceState != null) selectedTab = savedInstanceState.getInt("selected_tab", R.id.navigation_so_manager);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        ScreenInsets.install(findViewById(R.id.main), bottomNavigationView);
        checking = new android.widget.ProgressBar(this);
        checking.setContentDescription(getString(R.string.root_check));
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
            new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                (int) (40 * getResources().getDisplayMetrics().density),
                (int) (40 * getResources().getDisplayMetrics().density));
        params.topToTop = params.bottomToBottom = 0;
        params.startToStart = params.endToEnd = 0;
        ((android.view.ViewGroup) findViewById(R.id.main)).addView(checking, params);
        startRootCheck(false);
    }

    private void startRootCheck(boolean fresh) {
        if (checkingRoot || rootGranted || isFinishing() || isDestroyed()) return;
        checkingRoot = true;
        if (rootDialog != null) { rootDialog.dismiss(); rootDialog = null; }
        checking.setVisibility(android.view.View.VISIBLE);
        bottomNavigationView.setVisibility(android.view.View.INVISIBLE);
        findViewById(R.id.nav_host_fragment).setVisibility(android.view.View.INVISIBLE);
        RootAccess.request(fresh, granted -> {
            if (isFinishing() || isDestroyed()) return;
            checkingRoot = false;
            pendingRootUi = () -> {
                checking.setVisibility(android.view.View.GONE);
                if (!granted) {
                    rootDialog = RootDeniedDialog.show(this);
                    return;
                }
                rootGranted = true;
                findViewById(R.id.nav_host_fragment).setVisibility(android.view.View.VISIBLE);
                bottomNavigationView.setVisibility(android.view.View.VISIBLE);
                appListFragment = (AppListFragment) getSupportFragmentManager().findFragmentByTag("apps");
                soManagerFragment = (SoManagerFragment) getSupportFragmentManager().findFragmentByTag("library");
                settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settings");
                setupBottomNavigation();
                bottomNavigationView.setSelectedItemId(selectedTab);
            };
            renderRootResult();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", rootGranted ? bottomNavigationView.getSelectedItemId() : selectedTab);
        super.onSaveInstanceState(outState);
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

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

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