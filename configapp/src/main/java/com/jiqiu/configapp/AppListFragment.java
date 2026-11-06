package com.jiqiu.configapp;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.app.Dialog;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 应用列表Fragment
 */
public class AppListFragment extends Fragment implements AppListAdapter.OnAppToggleListener, AppListAdapter.OnAppClickListener {

    private static final java.util.concurrent.ExecutorService LOADER = ConfigManager.IO;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;
    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private TextInputEditText searchEditText;
    private ProgressBar progressBar;

    private boolean loadingApps;
    private List<AppInfo> allApps;
    private boolean hideSystemApps = false;
    private ConfigManager configManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupSearchView();
        view.findViewById(R.id.refresh_apps).setOnClickListener(v -> {
            if (loadingApps) return;
            AppCatalog.clear();
            AppIconLoader.clear();
            loadApps();
        });
        loadApps();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            if (AppCatalog.memory == null) loadApps();
            else if (allApps != null && configManager != null) {
                boolean changed = false;
                for (AppInfo app : allApps) {
                    boolean enabled = configManager.isAppEnabled(app.getPackageName());
                    if (enabled != app.isEnabled()) { app.setEnabled(enabled); changed = true; }
                }
                if (changed) adapter.refreshOrder();
                filterApps(searchEditText.getText().toString());
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        loadingApps = false;
        super.onDestroyView();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view_apps);
        searchEditText = view.findViewById(R.id.search_edit_text);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void setupRecyclerView() {
        adapter = new AppListAdapter();
        adapter.setOnAppToggleListener(this);
        adapter.setOnAppClickListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemViewCacheSize(6);
    }

    private void setupSearchView() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                String query = s.toString();
                pendingSearch = () -> filterApps(query);
                searchHandler.postDelayed(pendingSearch, 100);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        getView().findViewById(R.id.refresh_apps).setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(allApps == null ? View.GONE : View.VISIBLE);

        hideSystemApps = requireContext().getSharedPreferences("MyInjectorSettings", 0)
            .getBoolean("hide_system_apps", false);
        final android.content.Context context = requireContext().getApplicationContext();
        final View currentView = getView();
        LOADER.execute(() -> {
            try {
            ConfigManager manager = new ConfigManager(context);

            List<AppInfo> apps = AppCatalog.memory;
            if (apps == null) apps = AppCatalog.load(context);
            for (AppInfo app : apps) app.setEnabled(manager.isAppEnabled(app.getPackageName()));
            final List<AppInfo> result = apps;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (getView() != currentView || !isAdded()) return;
                loadingApps = false;
                currentView.findViewById(R.id.refresh_apps).setEnabled(true);
                configManager = manager;
                allApps = result;
                adapter.setAppList(result);
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                filterApps(searchEditText.getText().toString());
            });
            } catch (RuntimeException error) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (getView() != currentView || !isAdded()) return;
                    loadingApps = false;
                    currentView.findViewById(R.id.refresh_apps).setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    android.widget.Toast.makeText(context, Localized.text(R.string.refresh_failed), android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void filterApps(String query) {
        if (adapter != null) {
            adapter.filterApps(query, hideSystemApps);
        }
    }

    public void setHideSystemApps(boolean hideSystemApps) {
        this.hideSystemApps = hideSystemApps;
        if (searchEditText != null) filterApps(searchEditText.getText().toString());
    }

    @Override
    public void onAppToggle(AppInfo appInfo, boolean isEnabled) {
        android.content.Context context = requireContext().getApplicationContext();
        LOADER.execute(() -> {
            ConfigManager.ApplyResult result = configManager.setAppEnabled(appInfo.getPackageName(), isEnabled);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (result == ConfigManager.ApplyResult.FAILED) {
                    appInfo.setEnabled(!isEnabled);
                    if (getView() != null) {
                        adapter.refreshOrder();
                        filterApps(searchEditText.getText().toString());
                    }
                }
                int message = result == ConfigManager.ApplyResult.FAILED ? R.string.apply_failed
                    : result == ConfigManager.ApplyResult.STOP_FAILED ? R.string.apply_stop_failed
                    : result == ConfigManager.ApplyResult.RESTARTED ? R.string.apply_done : R.string.apply_saved;
                android.widget.Toast.makeText(context, Localized.text(message), android.widget.Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    public void onAppClick(AppInfo appInfo) {
        showAppConfigDialog(appInfo);
    }

    private void showAppConfigDialog(AppInfo appInfo) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_app_config, null);

        // Set app info
        ImageView appIcon = dialogView.findViewById(R.id.appIcon);
        TextView appName = dialogView.findViewById(R.id.appName);
        TextView packageName = dialogView.findViewById(R.id.packageName);
        RecyclerView soListRecyclerView = dialogView.findViewById(R.id.soListRecyclerView);
        TextView emptyText = dialogView.findViewById(R.id.emptyText);
        RadioGroup injectionMethodGroup = dialogView.findViewById(R.id.injectionMethodGroup);
        RadioButton radioStandardInjection = dialogView.findViewById(R.id.radioStandardInjection);
        RadioButton radioRiruInjection = dialogView.findViewById(R.id.radioRiruInjection);
        RadioButton radioCustomLinkerInjection = dialogView.findViewById(R.id.radioCustomLinkerInjection);
        RadioGroup gadgetConfigGroup = dialogView.findViewById(R.id.gadgetConfigGroup);
        RadioButton radioNoGadget = dialogView.findViewById(R.id.radioNoGadget);
        RadioButton radioUseGlobalGadget = dialogView.findViewById(R.id.radioUseGlobalGadget);
        RadioButton radioUseCustomGadget = dialogView.findViewById(R.id.radioUseCustomGadget);
        TextView tvGlobalGadgetInfo = dialogView.findViewById(R.id.tvGlobalGadgetInfo);
        com.google.android.material.button.MaterialButton btnConfigureGadget = dialogView.findViewById(R.id.btnConfigureGadget);

        try {
            appIcon.setImageDrawable(requireContext().getPackageManager().getApplicationIcon(appInfo.getPackageName()));
        } catch (PackageManager.NameNotFoundException ignored) {
            appIcon.setImageDrawable(requireContext().getPackageManager().getDefaultActivityIcon());
        }
        appName.setText(appInfo.getAppName());
        packageName.setText(appInfo.getPackageName());

        // Load current config
        String injectionMethod = configManager.getAppInjectionMethod(appInfo.getPackageName());
        if ("custom_linker".equals(injectionMethod)) {
            radioCustomLinkerInjection.setChecked(true);
        } else if ("riru".equals(injectionMethod)) {
            radioRiruInjection.setChecked(true);
        } else {
            radioStandardInjection.setChecked(true);
        }

        // Load gadget config
        boolean useGlobalGadget = configManager.getAppUseGlobalGadget(appInfo.getPackageName());
        ConfigManager.GadgetConfig appSpecificGadget = configManager.getAppGadgetConfig(appInfo.getPackageName());
        ConfigManager.GadgetConfig globalGadget = configManager.getGlobalGadgetConfig();
        final ConfigManager.GadgetConfig[] stagedGadget = {useGlobalGadget ? null : appSpecificGadget};

        // Update global gadget info
        if (globalGadget != null) {
            String info = Localized.text(R.string.text_global) + globalGadget.gadgetName;
            if (globalGadget.mode.equals("server")) {
                info += Localized.text(R.string.text_port) + globalGadget.port + ")";
            }
            tvGlobalGadgetInfo.setText(info);
        } else {
            tvGlobalGadgetInfo.setText(Localized.text(R.string.text_global_gadget_is_not_configured));
        }

        // Set initial radio selection
        if (!useGlobalGadget && appSpecificGadget != null) {
            radioUseCustomGadget.setChecked(true);
            btnConfigureGadget.setVisibility(View.VISIBLE);
            btnConfigureGadget.setEnabled(true);
        } else if (useGlobalGadget && globalGadget != null) {
            radioUseGlobalGadget.setChecked(true);
            btnConfigureGadget.setVisibility(View.GONE);
        } else {
            radioNoGadget.setChecked(true);
            btnConfigureGadget.setVisibility(View.GONE);
        }

        // Setup gadget radio group listener
        gadgetConfigGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioNoGadget) {
                btnConfigureGadget.setVisibility(View.GONE);

            } else if (checkedId == R.id.radioUseGlobalGadget) {
                btnConfigureGadget.setVisibility(View.GONE);

            } else if (checkedId == R.id.radioUseCustomGadget) {
                btnConfigureGadget.setVisibility(View.VISIBLE);
                btnConfigureGadget.setEnabled(true);

            }
        });

        // Configure button listener
        btnConfigureGadget.setOnClickListener(v -> {
            ConfigManager.GadgetConfig currentConfig = stagedGadget[0];
            if (currentConfig == null) {
                currentConfig = new ConfigManager.GadgetConfig();
            }

            GadgetConfigDialog dialog = new GadgetConfigDialog(
                getContext(),
                Localized.text(R.string.text_configure) + appInfo.getAppName() + Localized.text(R.string.text_gadget),
                currentConfig,
                config -> {

                    stagedGadget[0] = config;
                }
            );
            dialog.show();
        });

        // Setup SO list
        List<ConfigManager.SoFile> globalSoFiles = configManager.getAllSoFiles();
        List<ConfigManager.SoFile> appSoFiles = configManager.getAppSoFiles(appInfo.getPackageName());

        if (globalSoFiles.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            soListRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            soListRecyclerView.setVisibility(View.VISIBLE);

            SoSelectionAdapter soAdapter = new SoSelectionAdapter(globalSoFiles, appSoFiles);
            soListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            soListRecyclerView.setAdapter(soAdapter);
        }

        // Create dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(Localized.text(R.string.text_app_configuration))
                .setView(dialogView)
                .setMessage(R.string.apply_notice)
                .setPositiveButton(R.string.apply_and_save, (dialog, which) -> {
                    // Save injection method
                    String selectedMethod;
                    if (radioCustomLinkerInjection.isChecked()) {
                        selectedMethod = "custom_linker";
                    } else if (radioRiruInjection.isChecked()) {
                        selectedMethod = "riru";
                    } else {
                        selectedMethod = "standard";
                    }
                    List<ConfigManager.SoFile> selected = soListRecyclerView.getAdapter() == null
                        ? new ArrayList<>() : ((SoSelectionAdapter) soListRecyclerView.getAdapter()).getSelectedSoFiles();
                    boolean global = radioUseGlobalGadget.isChecked();
                    ConfigManager.GadgetConfig gadget = radioUseCustomGadget.isChecked() ? stagedGadget[0] : null;
                    android.content.Context appContext = requireContext().getApplicationContext();
                    LOADER.execute(() -> {
                        ConfigManager.ApplyResult result = configManager.applyAppConfiguration(
                            appInfo.getPackageName(), selectedMethod, selected, global, gadget);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            int message = result == ConfigManager.ApplyResult.RESTARTED ? R.string.apply_done
                                : result == ConfigManager.ApplyResult.FAILED ? R.string.apply_failed
                                : result == ConfigManager.ApplyResult.STOP_FAILED ? R.string.apply_stop_failed : R.string.apply_saved;
                            android.widget.Toast.makeText(appContext, Localized.text(message), android.widget.Toast.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton(Localized.text(R.string.text_cancel), null);

        builder.show();
    }

    // Inner class for SO selection adapter
    private static class SoSelectionAdapter extends RecyclerView.Adapter<SoSelectionAdapter.ViewHolder> {
        private List<ConfigManager.SoFile> globalSoFiles;
        private List<ConfigManager.SoFile> selectedSoFiles;

        public SoSelectionAdapter(List<ConfigManager.SoFile> globalSoFiles, List<ConfigManager.SoFile> appSoFiles) {
            this.globalSoFiles = globalSoFiles;
            this.selectedSoFiles = new ArrayList<>(appSoFiles);
        }

        public List<ConfigManager.SoFile> getSelectedSoFiles() {
            return new ArrayList<>(selectedSoFiles);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_so_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConfigManager.SoFile soFile = globalSoFiles.get(position);
            holder.bind(soFile, selectedSoFiles);
        }

        @Override
        public int getItemCount() {
            return globalSoFiles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView nameText;
            TextView pathText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.checkBox);
                nameText = itemView.findViewById(R.id.textName);
                pathText = itemView.findViewById(R.id.textPath);
            }

            void bind(ConfigManager.SoFile soFile, List<ConfigManager.SoFile> selectedList) {
                nameText.setText(soFile.name);
                pathText.setText(soFile.originalPath);

                // Check if this SO is selected
                boolean isSelected = false;
                for (ConfigManager.SoFile selected : selectedList) {
                    if (selected.storedPath.equals(soFile.storedPath)) {
                        isSelected = true;
                        break;
                    }
                }

                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(isSelected);

                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedList.add(soFile);
                    } else {
                        selectedList.removeIf(s -> s.storedPath.equals(soFile.storedPath));
                    }
                });

                itemView.setOnClickListener(v -> checkBox.toggle());
            }
        }
    }

}
