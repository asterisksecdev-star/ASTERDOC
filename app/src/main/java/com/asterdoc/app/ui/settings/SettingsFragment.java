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
import com.asterdoc.app.auth.AuthManager;
import com.asterdoc.app.billing.BillingManager;
import com.asterdoc.app.ui.auth.AuthActivity;
import com.asterdoc.app.ui.faq.FaqActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private BillingManager billingManager;
    private AuthManager authManager;

    private TextView tvUserName;
    private TextView tvUserEmail;
    private MaterialButton btnSignOut;
    private TextView tvProTitle;
    private TextView tvProDesc;
    private MaterialButton btnUpgradePro;
    private TextView tvCurrentLanguage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        billingManager = BillingManager.getInstance(requireContext());
        authManager = AuthManager.getInstance(requireContext());

        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        btnSignOut = view.findViewById(R.id.btnSignOut);

        tvProTitle = view.findViewById(R.id.tvProTitle);
        tvProDesc = view.findViewById(R.id.tvProDesc);
        btnUpgradePro = view.findViewById(R.id.btnUpgradePro);
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage);

        tvUserName.setText(authManager.getUserName());
        tvUserEmail.setText(authManager.getUserEmail());

        updateCurrentLanguageLabel();

        btnSignOut.setOnClickListener(v -> {
            authManager.logout();
            BrandedToast.show(requireContext(), getString(R.string.msg_signed_out));
            startActivity(new Intent(requireContext(), AuthActivity.class));
            requireActivity().finish();
        });

        updateProUI();

        view.findViewById(R.id.llStorageSetting).setOnClickListener(v -> showStorageInfoDialog());
        view.findViewById(R.id.llClearCache).setOnClickListener(v -> clearTemporaryFilesOnly());
        view.findViewById(R.id.llPrivacyPolicy).setOnClickListener(v -> showPrivacyDialog());

        View llLanguage = view.findViewById(R.id.llLanguageSetting);
        if (llLanguage != null) {
            llLanguage.setOnClickListener(v -> showLanguageDialog());
        }

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

        return view;
    }

    private void updateCurrentLanguageLabel() {
        if (tvCurrentLanguage == null) return;
        String code = LocaleHelper.getLanguage(requireContext());
        switch (code) {
            case "fr":
                tvCurrentLanguage.setText("Français (French) 🇫🇷");
                break;
            case "es":
                tvCurrentLanguage.setText("Español (Spanish) 🇪🇸");
                break;
            case "de":
                tvCurrentLanguage.setText("Deutsch (German) 🇩🇪");
                break;
            case "pt":
                tvCurrentLanguage.setText("Português (Portuguese) 🇵🇹");
                break;
            case "zh":
                tvCurrentLanguage.setText("中文 (Chinese) 🇨🇳");
                break;
            case "ar":
                tvCurrentLanguage.setText("العربية (Arabic) 🇸🇦");
                break;
            case "rw":
                tvCurrentLanguage.setText("Ikinyarwanda (Kinyarwanda) 🇷🇼");
                break;
            default:
                tvCurrentLanguage.setText("English 🌐");
                break;
        }
    }

    private void showLanguageDialog() {
        String[] languages = {
                "English 🌐",
                "Español (Spanish) 🇪🇸",
                "Français (French) 🇫🇷",
                "Deutsch (German) 🇩🇪",
                "Português (Portuguese) 🇵🇹",
                "中文 (Chinese) 🇨🇳",
                "العربية (Arabic) 🇸🇦",
                "Ikinyarwanda (Kinyarwanda) 🇷🇼"
        };
        String[] codes = {"en", "es", "fr", "de", "pt", "zh", "ar", "rw"};

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_language))
                .setIcon(R.mipmap.ic_launcher)
                .setItems(languages, (d, which) -> {
                    String code = codes[which];
                    setLocale(code);
                    BrandedToast.show(requireContext(), getString(R.string.msg_lang_updated));
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finishAffinity();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getListView() != null) {
                dialog.getListView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
        });
        dialog.show();
    }

    private void setLocale(String languageCode) {
        try {
            LocaleHelper.setLocale(requireContext(), languageCode);
        } catch (Exception ignored) {}
    }

    private void updateProUI() {
        tvProTitle.setText(R.string.pro_title);
        tvProDesc.setText(R.string.pro_desc_unlocked);
        btnUpgradePro.setText(R.string.premium_active);
        btnUpgradePro.setEnabled(true);
        btnUpgradePro.setOnClickListener(v -> BrandedToast.show(requireContext(), getString(R.string.pro_desc_unlocked)));
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
                    .setIcon(R.mipmap.ic_launcher)
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
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setMessage(getString(R.string.privacy_policy_message));
        builder.setPositiveButton(getString(R.string.close), null);
        builder.setNegativeButton(getString(R.string.delete_account_btn), (dialog, which) -> showDeleteAccountDialog());
        builder.show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_account_title))
                .setMessage(getString(R.string.delete_account_msg))
                .setIcon(R.mipmap.ic_launcher)
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    authManager.deleteAccount(requireContext());
                    BrandedToast.show(requireContext(), getString(R.string.account_deleted_toast));
                    startActivity(new Intent(requireContext(), AuthActivity.class));
                    requireActivity().finish();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
}
