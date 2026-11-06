package com.jiqiu.configapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SoManagerFragment extends Fragment {
    
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private SoListAdapter adapter;
    private ConfigManager configManager;
    private List<ConfigManager.SoFile> globalSoFiles = new ArrayList<>();
    
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> fileBrowserLauncher;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        configManager = new ConfigManager(requireContext());
        // Ensure module directories exist
        configManager.ensureModuleDirectories();
        
        // Initialize file picker
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleFileSelection(uri);
                    }
                }
            }
        );
        
        // Initialize file browser
        fileBrowserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra(FileBrowserActivity.EXTRA_SELECTED_PATH);
                    if (path != null) {
                        showDeleteOriginalDialog(path);
                    }
                }
            }
        );
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_so_manager, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.emptyView);
        FloatingActionButton fabAdd = view.findViewById(R.id.fabAdd);
        
        // Setup RecyclerView
        adapter = new SoListAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        adapter.setOnSoFileActionListener(this::showDeleteConfirmation);
        
        // Setup FAB
        fabAdd.setOnClickListener(v -> showAddSoDialog());
        
        // Check root access
        if (!configManager.isRootAvailable()) {
            Toast.makeText(getContext(), Localized.text(R.string.text_root_access_is_required), Toast.LENGTH_LONG).show();
        } else {
            configManager.ensureModuleDirectories();
            // Also ensure common directories exist
            Shell.cmd("mkdir -p /data/local/tmp").exec();
            Shell.cmd("chmod 777 /data/local/tmp").exec();
            loadSoFiles();
        }
    }
    
    private void loadSoFiles() {
        // Load global SO files from config
        globalSoFiles = configManager.getAllSoFiles();
        updateUI();
    }
    
    private void updateUI() {
        if (globalSoFiles.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setSoFiles(globalSoFiles);
        }
    }
    
    private void showAddSoDialog() {
        String[] options = {Localized.text(R.string.text_browse_files), Localized.text(R.string.text_choose_with_a_file_manager), Localized.text(R.string.text_enter_a_path_manually)};
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_add_an_so_file))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openFileBrowser();
                    } else if (which == 1) {
                        openFilePicker();
                    } else {
                        showPathInputDialog();
                    }
                })
                .show();
    }
    
    private void openFileBrowser() {
        // Show path selection dialog first
        String[] paths = {
            "/data/local/tmp",
            "/sdcard",
            "/sdcard/Download",
            "/storage/emulated/0",
            Localized.text(R.string.text_custom_path)
        };
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_choose_a_starting_folder))
                .setItems(paths, (dialog, which) -> {
                    if (which == paths.length - 1) {
                        // Custom path
                        showCustomPathDialog();
                    } else {
                        Intent intent = new Intent(getContext(), FileBrowserActivity.class);
                        intent.putExtra(FileBrowserActivity.EXTRA_START_PATH, paths[which]);
                        intent.putExtra(FileBrowserActivity.EXTRA_FILE_FILTER, ".so");
                        fileBrowserLauncher.launch(intent);
                    }
                })
                .show();
    }
    
    private void showCustomPathDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_input, null);
        android.widget.EditText editText = view.findViewById(android.R.id.edit);
        editText.setText("/");
        editText.setHint(Localized.text(R.string.text_enter_a_starting_path));
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_custom_starting_path))
                .setView(view)
                .setPositiveButton(Localized.text(R.string.text_ok), (dialog, which) -> {
                    String path = editText.getText().toString().trim();
                    if (!path.isEmpty()) {
                        Intent intent = new Intent(getContext(), FileBrowserActivity.class);
                        intent.putExtra(FileBrowserActivity.EXTRA_START_PATH, path);
                        intent.putExtra(FileBrowserActivity.EXTRA_FILE_FILTER, ".so");
                        fileBrowserLauncher.launch(intent);
                    }
                })
                .setNegativeButton(Localized.text(R.string.text_cancel), null)
                .show();
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Add MIME types that might help filter SO files
        String[] mimeTypes = {"application/octet-stream", "application/x-sharedlib", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        // Suggest starting location
        intent.putExtra("android.provider.extra.INITIAL_URI", 
            android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
        filePickerLauncher.launch(intent);
    }
    
    private void showPathInputDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_input, null);
        android.widget.EditText editText = view.findViewById(android.R.id.edit);
        editText.setText("/data/local/tmp/");
        editText.setHint("/data/local/tmp/example.so");
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_enter_the_so_file_path))
                .setView(view)
                .setPositiveButton(Localized.text(R.string.text_add), (dialog, which) -> {
                    String path = editText.getText().toString().trim();
                    if (!path.isEmpty()) {
                        showDeleteOriginalDialog(path);
                    }
                })
                .setNegativeButton(Localized.text(R.string.text_cancel), null)
                .show();
    }
    
    private void handleFileSelection(Uri uri) {
        // Get real path from URI using proper URI handling
        String path = FileUtils.getRealPathFromUri(requireContext(), uri);
        if (path != null) {
            showDeleteOriginalDialog(path);
        } else {
            Toast.makeText(getContext(), Localized.text(R.string.text_unable_to_resolve_the_path_try_another_method), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDeleteOriginalDialog(String path) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_delete_original_file))
                .setMessage(Localized.text(R.string.text_delete_the_original_so_file_n_npath) + path)
                .setPositiveButton(Localized.text(R.string.text_delete_original_file), (dialog, which) -> {
                    addSoFile(path, true);
                })
                .setNegativeButton(Localized.text(R.string.text_keep_original), (dialog, which) -> {
                    addSoFile(path, false);
                })
                .setNeutralButton(Localized.text(R.string.text_cancel), null)
                .show();
    }
    
    private void addSoFile(String path, boolean deleteOriginal) {
        // Verify file exists
        Shell.Result result = Shell.cmd("test -f \"" + path + "\" && echo 'exists'").exec();
        if (!result.isSuccess() || result.getOut().isEmpty()) {
            Toast.makeText(getContext(), Localized.text(R.string.text_file_does_not_exist) + path, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add to global SO files
        configManager.addGlobalSoFile(path, deleteOriginal);
        
        // Reload the list
        loadSoFiles();
        Toast.makeText(getContext(), Localized.text(R.string.text_so_file_added), Toast.LENGTH_SHORT).show();
    }
    
    private void showDeleteConfirmation(ConfigManager.SoFile soFile) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Localized.text(R.string.text_delete_so_file))
                .setMessage(Localized.text(R.string.text_delete) + soFile.name + Localized.text(R.string.text_))
                .setPositiveButton(Localized.text(R.string.text_delete_102), (dialog, which) -> {
                    deleteSoFile(soFile);
                })
                .setNegativeButton(Localized.text(R.string.text_cancel), null)
                .show();
    }
    
    private void deleteSoFile(ConfigManager.SoFile soFile) {
        configManager.removeGlobalSoFile(soFile);
        loadSoFiles();
        Toast.makeText(getContext(), Localized.text(R.string.text_so_file_deleted), Toast.LENGTH_SHORT).show();
    }
}