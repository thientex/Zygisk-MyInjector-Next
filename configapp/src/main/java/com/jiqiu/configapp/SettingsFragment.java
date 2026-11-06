package com.jiqiu.configapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 设置Fragment
 */
public class SettingsFragment extends Fragment {
    
    private static final String PREFS_NAME = "MyInjectorSettings";
    private static final String KEY_HIDE_SYSTEM_APPS = "hide_system_apps";
    
    private RadioGroup radioGroupFilter;
    private RadioButton radioShowAll;
    private RadioButton radioHideSystem;
    private EditText editInjectionDelay;
    private TextView tvGlobalGadgetStatus;
    private Button btnConfigureGlobalGadget;
    private ConfigManager configManager;
    
    private SharedPreferences sharedPreferences;
    private OnSettingsChangeListener settingsChangeListener;
    
    public interface OnSettingsChangeListener {
        void onHideSystemAppsChanged(boolean hideSystemApps);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        initSharedPreferences();
        loadSettings();
        setupListeners();
        view.findViewById(R.id.clean_config).setOnClickListener(v -> confirmMaintenance(false));
        view.findViewById(R.id.factory_reset).setOnClickListener(v -> confirmMaintenance(true));
        android.widget.Spinner themes = view.findViewById(R.id.theme_picker);
        String[] themeLabels = {getString(R.string.appearance_system), getString(R.string.appearance_light), getString(R.string.appearance_dark)};
        int[] modes = {-1, 1, 2};
        themes.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), R.layout.spinner_item, themeLabels));
        int mode = sharedPreferences.getInt("theme", -1);
        for (int i = 0; i < modes.length; i++) if (modes[i] == mode) themes.setSelection(i);
        themes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, View row, int position, long id) {
                if (modes[position] != sharedPreferences.getInt("theme", -1)) {
                    sharedPreferences.edit().putInt("theme", modes[position]).apply();
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(modes[position]);
                }
            }
        });
        android.widget.Spinner languages = view.findViewById(R.id.language_picker);
        String[] tags = {"", "en", "vi", "zh"};
        String[] labels = {getString(R.string.language_system), getString(R.string.language_english), getString(R.string.language_vietnamese), getString(R.string.language_chinese)};
        android.widget.ArrayAdapter<String> languageAdapter = new android.widget.ArrayAdapter<>(
            requireContext(), R.layout.spinner_item, labels);
        languages.setAdapter(languageAdapter);
        String selected = sharedPreferences.getString("language", "");
        for (int i = 0; i < tags.length; i++) if (tags[i].equals(selected)) languages.setSelection(i);
        languages.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                if (!tags[position].equals(sharedPreferences.getString("language", ""))) {
                    sharedPreferences.edit().putString("language", tags[position]).apply();
                    AppCatalog.clear();
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(tags[position]));
                }
            }
        });
    }
    
    private void initViews(View view) {
        radioGroupFilter = view.findViewById(R.id.radio_group_filter);
        radioShowAll = view.findViewById(R.id.radio_show_all);
        radioHideSystem = view.findViewById(R.id.radio_hide_system);
        editInjectionDelay = view.findViewById(R.id.editInjectionDelay);
        tvGlobalGadgetStatus = view.findViewById(R.id.tvGlobalGadgetStatus);
        btnConfigureGlobalGadget = view.findViewById(R.id.btnConfigureGlobalGadget);
        
        configManager = new ConfigManager(getContext());
    }
    
    private void initSharedPreferences() {
        sharedPreferences = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    private void loadSettings() {
        boolean hideSystemApps = sharedPreferences.getBoolean(KEY_HIDE_SYSTEM_APPS, false);
        
        if (hideSystemApps) {
            radioHideSystem.setChecked(true);
        } else {
            radioShowAll.setChecked(true);
        }
        
        // Load injection delay
        int injectionDelay = configManager.getInjectionDelay();
        editInjectionDelay.setText(String.valueOf(injectionDelay));
        
        // Load global gadget status
        updateGlobalGadgetStatus();
    }
    
    private void setupListeners() {
        radioGroupFilter.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean hideSystemApps = (checkedId == R.id.radio_hide_system);
                
                // 保存设置
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(KEY_HIDE_SYSTEM_APPS, hideSystemApps);
                editor.apply();
                
                // 通知设置变化
                if (settingsChangeListener != null) {
                    settingsChangeListener.onHideSystemAppsChanged(hideSystemApps);
                }
            }
        });
        
        // Injection delay listener
        editInjectionDelay.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        int delay = Integer.parseInt(text);
                        // Limit delay between 0 and 60 seconds
                        if (delay < 0) delay = 0;
                        if (delay > 60) delay = 60;
                        
                        final int value = delay;
                        ConfigManager.IO.execute(() -> configManager.setInjectionDelay(value));
                    } catch (NumberFormatException e) {
                        // Ignore invalid input
                    }
                }
            }
        });
        
        // Global gadget configuration button
        btnConfigureGlobalGadget.setOnClickListener(v -> {
            showGlobalGadgetConfigDialog();
        });
    }
    
    public void setOnSettingsChangeListener(OnSettingsChangeListener listener) {
        this.settingsChangeListener = listener;
    }
    
    public boolean isHideSystemApps() {
        return sharedPreferences.getBoolean(KEY_HIDE_SYSTEM_APPS, false);
    }
    
    private void updateGlobalGadgetStatus() {
        ConfigManager.GadgetConfig globalGadget = configManager.getGlobalGadgetConfig();
        if (globalGadget != null) {
            String status = Localized.text(R.string.text_configured) + globalGadget.gadgetName;
            if (globalGadget.mode.equals("server")) {
                status += Localized.text(R.string.text_server_port) + globalGadget.port + ")";
            } else {
                status += Localized.text(R.string.text_script_mode_106);
            }
            tvGlobalGadgetStatus.setText(status);
        } else {
            tvGlobalGadgetStatus.setText(Localized.text(R.string.text_not_configured));
        }
    }
    
    private void showGlobalGadgetConfigDialog() {
        // Use existing GadgetConfigDialog
        GadgetConfigDialog dialog = new GadgetConfigDialog(
            getContext(),
            Localized.text(R.string.text_global_gadget_configuration),
            configManager.getGlobalGadgetConfig(),
            gadgetConfig -> {
                // Save global gadget configuration
                android.content.Context context = requireContext().getApplicationContext();
                ConfigManager.IO.execute(() -> {
                    ConfigManager.ApplyResult result = configManager.setGlobalGadgetConfig(gadgetConfig);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (getView() != null) updateGlobalGadgetStatus();
                        int message = result == ConfigManager.ApplyResult.FAILED ? R.string.apply_failed
                            : result == ConfigManager.ApplyResult.STOP_FAILED ? R.string.apply_stop_failed
                            : result == ConfigManager.ApplyResult.RESTARTED ? R.string.apply_done : R.string.apply_saved;
                        android.widget.Toast.makeText(context, Localized.text(message), android.widget.Toast.LENGTH_LONG).show();
                    });
                });
            }
        );
        dialog.show();
    }
    private boolean maintaining;
    private void confirmMaintenance(boolean reset) {
        if (maintaining) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(reset ? R.string.factory_reset : R.string.clean_config)
            .setMessage(reset ? R.string.reset_confirm : R.string.clean_confirm)
            .setNegativeButton(R.string.text_cancel, null)
            .setPositiveButton(reset ? R.string.factory_reset : R.string.clean_config, (dialog, which) -> runMaintenance(reset))
            .show();
    }
    private void runMaintenance(boolean reset) {
        if (maintaining) return;
        maintaining = true;
        View screen = getView();
        screen.findViewById(R.id.clean_config).setEnabled(false);
        screen.findViewById(R.id.factory_reset).setEnabled(false);
        android.content.Context context = requireContext().getApplicationContext();
        ConfigManager.IO.execute(() -> {
            int cleaned = reset ? 0 : configManager.cleanObsoleteConfigurations();
            boolean success = reset ? configManager.factoryReset() : cleaned >= 0;
            if (reset && success) {
                context.getSharedPreferences("MyInjectorSettings", 0).edit().clear().commit();
                context.getSharedPreferences("app_catalog", 0).edit().clear().commit();
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                maintaining = false;
                String message = !success ? Localized.text(R.string.maintenance_failed)
                    : reset ? Localized.text(R.string.reset_done) : String.format(java.util.Locale.getDefault(), Localized.text(R.string.clean_done), cleaned);
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
                AppCatalog.clear();
                AppIconLoader.clear();
                if (getView() == screen) {
                    screen.findViewById(R.id.clean_config).setEnabled(true);
                    screen.findViewById(R.id.factory_reset).setEnabled(true);
                }
                if (reset && isAdded()) {
                    if (success) {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList());
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    }
                    requireActivity().recreate();
                }
            });
        });
    }

}
