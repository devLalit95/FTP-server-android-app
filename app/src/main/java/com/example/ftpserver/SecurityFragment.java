package com.example.ftpserver;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.ftpserver.databinding.FragmentSecurityBinding;

public class SecurityFragment extends Fragment {

    private FragmentSecurityBinding binding;
    private ServerPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSecurityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        preferences = new ServerPreferences(requireContext());
        
        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        loadPreferences();

        binding.buttonSave.setOnClickListener(v -> {
            savePreferences();
            Toast.makeText(getContext(), "Security settings saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadPreferences() {
        binding.editTextUsername.setText(preferences.getUsername());
        binding.editTextPassword.setText(preferences.getPassword());
        binding.editTextPort.setText(String.valueOf(preferences.getPort()));
    }

    private void savePreferences() {
        String username = binding.editTextUsername.getText() != null ? binding.editTextUsername.getText().toString().trim() : "lalit";
        String password = binding.editTextPassword.getText() != null ? binding.editTextPassword.getText().toString() : "lalit";
        int port;
        try {
            port = Integer.parseInt(binding.editTextPort.getText().toString().trim());
        } catch (Exception e) {
            port = 2121;
        }

        preferences.setUsername(username);
        preferences.setPassword(password);
        preferences.setPort(port);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
