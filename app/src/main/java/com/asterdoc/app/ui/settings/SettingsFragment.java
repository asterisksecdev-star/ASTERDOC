package com.asterdoc.app.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.asterdoc.app.MainActivity;
import com.asterdoc.app.R;
import com.asterdoc.app.ui.faq.FaqActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.UserMessagingPlatform;

import java.io.File;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        view.findViewById(R.id.llStorageSetting).setOnClickListener(v -> showStorageInfoDialog());
        view.findViewById(R.id.llClearCache).setOnClickListener(v -> clearTemporaryFilesOnly());
        view.findViewById(R.id.llPrivacyPolicy).setOnClickListener(v -> showPrivacyDialog());

        View llFaq = view.findViewById(R.id.llFaqSetting);
        if (llFaq != null) {
            llFaq.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), FaqActivity.class));
            });
        }

        View llSupport = view.findViewById(R.id.llSupportCreator);
        if (llSupport != null) {
            llSupport.setOnClickListener(v -> MainActivity.showDonationDialogIfNeeded(requireContext(), true));
        }

        View llPrivacyOptions = view.findViewById(R.id.llPrivacyOptionsSetting);
        View dividerPrivacyOptions = view.findViewById(R.id.dividerPrivacyOptions);
        if (llPrivacyOptions != null && dividerPrivacyOptions != null) {
            try {
                ConsentInformation consentInfo = UserMessagingPlatform.getConsentInformation(requireContext());
                if (consentInfo.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                    llPrivacyOptions.setVisibility(View.VISIBLE);
                    dividerPrivacyOptions.setVisibility(View.VISIBLE);
                    llPrivacyOptions.setOnClickListener(v -> {
                        UserMessagingPlatform.showPrivacyOptionsForm(requireActivity(), formError -> {
                            if (formError != null) {
                                BrandedToast.show(requireContext(), "Error showing privacy options: " + formError.getMessage());
                            }
                        });
                    });
                } else {
                    llPrivacyOptions.setVisibility(View.GONE);
                    dividerPrivacyOptions.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
                llPrivacyOptions.setVisibility(View.GONE);
                dividerPrivacyOptions.setVisibility(View.GONE);
            }
        }

        return view;
    }

    private void showStorageInfoDialog() {
        try {
            long internalSize = getDirSize(requireContext().getCacheDir());
            File extCache = requireContext().getExternalCacheDir();
            long totalCacheSize = internalSize + (extCache != null ? getDirSize(extCache) : 0);
            String sizeFormatted = formatBytes(totalCacheSize);

            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.storage_cache))
                    .setMessage("Current Application Cache Usage:\n" + sizeFormatted + "\n\nTemporary conversion files, cached document thumbnails, and viewer assets are managed here.")
                    .setIcon(R.drawable.asterine_logo_image)
                    .setPositiveButton("Clear App Cache", (dialog, which) -> clearCache())
                    .setNegativeButton(getString(R.string.close), null)
                    .show();
        } catch (Exception e) {
            BrandedToast.show(requireContext(), getString(R.string.err_cache_clear_failed));
        }
    }

    private void clearCache() {
        try {
            File cacheDir = requireContext().getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDir(cacheDir);
            }
            BrandedToast.show(requireContext(), getString(R.string.msg_cache_cleared));
        } catch (Exception e) {
            BrandedToast.show(requireContext(), getString(R.string.err_cache_clear_failed));
        }
    }

    private void clearTemporaryFilesOnly() {
        try {
            File cacheDir = requireContext().getCacheDir();
            long deletedBytes = 0;
            if (cacheDir != null && cacheDir.isDirectory()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().toLowerCase();
                        if (name.startsWith("temp_") || name.startsWith("open_excel_") || name.startsWith("arranger_") || name.startsWith("sign_pdf") || name.endsWith(".tmp")) {
                            deletedBytes += f.length();
                            boolean ignored = f.delete();
                        }
                    }
                }
            }
            BrandedToast.show(requireContext(), "Cleared " + formatBytes(deletedBytes) + " of temporary files");
        } catch (Exception e) {
            BrandedToast.show(requireContext(), getString(R.string.err_cache_clear_failed));
        }
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        size += getDirSize(f);
                    } else {
                        size += f.length();
                    }
                }
            }
        }
        return size;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void showPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.privacy_policy));
        builder.setIcon(R.drawable.asterine_logo_image);
        builder.setMessage(getString(R.string.privacy_policy_message));
        builder.setPositiveButton(getString(R.string.close), null);
        builder.show();
    }
}
