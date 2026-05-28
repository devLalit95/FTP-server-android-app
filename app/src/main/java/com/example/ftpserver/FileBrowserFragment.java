package com.example.ftpserver;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.ftpserver.databinding.FragmentFileBrowserBinding;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileBrowserFragment extends Fragment {

    private FragmentFileBrowserBinding binding;
    private File currentDirectory;
    private FileAdapter adapter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileBrowserBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Handle window insets (notch/status bar/navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Only apply top padding for status bar/notch. Bottom is handled by layout.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        currentDirectory = Environment.getExternalStorageDirectory();
        setupRecyclerView();
        updateFileList();

        binding.fabCreateFolder.setOnClickListener(v -> showCreateFolderDialog());
    }

    private void setupRecyclerView() {
        adapter = new FileAdapter(new ArrayList<>(), new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(File file) {
                if (file.isDirectory()) {
                    currentDirectory = file;
                    updateFileList();
                } else {
                    Toast.makeText(getContext(), "Opening " + file.getName(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onMenuClick(File file, View anchor) {
                showFileMenu(file, anchor);
            }
        });

        binding.recyclerViewFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewFiles.setAdapter(adapter);
    }

    private void updateFileList() {
        binding.textViewPath.setText(currentDirectory.getAbsolutePath());
        
        File[] filesArray = currentDirectory.listFiles();
        List<File> files = new ArrayList<>();
        
        // Add ".." if not at root
        if (!currentDirectory.equals(Environment.getExternalStorageDirectory())) {
            files.add(new File(currentDirectory.getParent(), ".."));
        }

        if (filesArray != null) {
            List<File> sortedFiles = Arrays.asList(filesArray);
            Collections.sort(sortedFiles, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
            files.addAll(sortedFiles);
        }

        adapter.setFiles(files);
    }

    private void showFileMenu(File file, View anchor) {
        if (file.getName().equals("..")) return;

        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add("Rename");
        popup.getMenu().add("Delete");
        
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Rename")) {
                showRenameDialog(file);
            } else if (item.getTitle().equals("Delete")) {
                showDeleteConfirmDialog(file);
            }
            return true;
        });
        popup.show();
    }

    private void showCreateFolderDialog() {
        EditText editText = new EditText(getContext());
        editText.setHint("Folder name");
        new AlertDialog.Builder(getContext())
                .setTitle("New Folder")
                .setView(editText)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = editText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File newFolder = new File(currentDirectory, name);
                        if (newFolder.mkdir()) {
                            updateFileList();
                        } else {
                            Toast.makeText(getContext(), "Failed to create folder", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(File file) {
        EditText editText = new EditText(getContext());
        editText.setText(file.getName());
        new AlertDialog.Builder(getContext())
                .setTitle("Rename")
                .setView(editText)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        File destination = new File(file.getParentFile(), newName);
                        if (file.renameTo(destination)) {
                            updateFileList();
                        } else {
                            Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmDialog(File file) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete " + file.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (deleteRecursive(file)) {
                        updateFileList();
                    } else {
                        Toast.makeText(getContext(), "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    public boolean onBackPressed() {
        if (!currentDirectory.equals(Environment.getExternalStorageDirectory())) {
            currentDirectory = currentDirectory.getParentFile();
            updateFileList();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
