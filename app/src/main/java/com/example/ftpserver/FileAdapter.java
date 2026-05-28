package com.example.ftpserver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ftpserver.databinding.ItemFileBinding;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<File> files;
    private final OnFileClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public interface OnFileClickListener {
        void onFileClick(File file);
        void onMenuClick(File file, View anchor);
    }

    public FileAdapter(List<File> files, OnFileClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    public void setFiles(List<File> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFileBinding binding = ItemFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new FileViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.bind(file, listener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private final ItemFileBinding binding;

        public FileViewHolder(ItemFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(File file, OnFileClickListener listener) {
            if (file.getName().equals("..")) {
                binding.textViewFileName.setText("Go Back");
                binding.textViewFileInfo.setText("Parent Directory");
                binding.imageViewIcon.setImageResource(android.R.drawable.ic_menu_revert);
                binding.buttonMenu.setVisibility(View.GONE);
            } else {
                binding.textViewFileName.setText(file.getName());
                String info = dateFormat.format(new Date(file.lastModified()));
                if (file.isFile()) {
                    info += " • " + formatSize(file.length());
                    binding.imageViewIcon.setImageResource(android.R.drawable.ic_menu_gallery); // Default icon
                } else {
                    binding.imageViewIcon.setImageResource(android.R.drawable.ic_menu_directions); // Folder icon
                }
                binding.textViewFileInfo.setText(info);
                binding.buttonMenu.setVisibility(View.VISIBLE);
            }

            binding.cardFile.setOnClickListener(v -> listener.onFileClick(file));
            binding.buttonMenu.setOnClickListener(v -> listener.onMenuClick(file, v));
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }
}
