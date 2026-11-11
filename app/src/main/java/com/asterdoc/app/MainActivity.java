package com.asterdoc.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.asterdoc.app.auth.AppLockService;
import com.asterdoc.app.data.AsterineStorageManager;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.ui.files.FilesFragment;
import com.asterdoc.app.ui.home.HomeFragment;
import com.asterdoc.app.ui.onboarding.OnboardingActivity;
import com.asterdoc.app.ui.settings.SettingsFragment;
import com.asterdoc.app.ui.viewer.ExcelViewerActivity;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Fragment homeFragment;
    private Fragment filesFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(OnboardingActivity.PREF_ONBOARDING, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(OnboardingActivity.KEY_COMPLETED, false)) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
            return;
        }

        try {
            startService(new Intent(this, AppLockService.class));
        } catch (Exception ignored) {}

        Executors.newSingleThreadExecutor().execute(() -> {
            DocumentRepository.getInstance(getApplicationContext());
        });

        setContentView(R.layout.activity_main);

        com.asterdoc.app.ads.AdManager.loadBannerAd(this, findViewById(R.id.adContainerMain));

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            filesFragment = new FilesFragment();
            settingsFragment = new SettingsFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, homeFragment, "HOME")
                    .add(R.id.fragmentContainer, filesFragment, "FILES").hide(filesFragment)
                    .add(R.id.fragmentContainer, settingsFragment, "SETTINGS").hide(settingsFragment)
                    .commit();
            activeFragment = homeFragment;
        } else {
            homeFragment = getSupportFragmentManager().findFragmentByTag("HOME");
            filesFragment = getSupportFragmentManager().findFragmentByTag("FILES");
            settingsFragment = getSupportFragmentManager().findFragmentByTag("SETTINGS");
            activeFragment = homeFragment;
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment targetFragment = null;

            if (itemId == R.id.navigation_home) {
                targetFragment = homeFragment;
            } else if (itemId == R.id.navigation_files) {
                targetFragment = filesFragment;
            } else if (itemId == R.id.navigation_settings) {
                targetFragment = settingsFragment;
            }

            if (targetFragment != null && targetFragment != activeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .hide(activeFragment)
                        .show(targetFragment)
                        .commit();
                activeFragment = targetFragment;
                return true;
            }
            return targetFragment != null;
        });

        showPrivacyPolicyDialogIfNeeded(this);

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction())) && intent.getData() != null) {
            Uri uri = intent.getData();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String originalName = getFileNameFromUri(this, uri);
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) return;

                    File destFile = AsterineStorageManager.getFileForOperation(this, null, originalName);
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    is.close();

                    String lower = originalName.toLowerCase();
                    String type = "PDF";
                    if (lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".rtf") || lower.endsWith(".odt")) {
                        type = "WORD";
                    } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
                        type = "EXCEL";
                    } else if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md") || lower.endsWith(".xml") || lower.endsWith(".json")) {
                        type = "TXT";
                    }
                    final String docType = type;

                    DocumentItem item = new DocumentItem(
                            String.valueOf(System.currentTimeMillis()),
                            originalName,
                            destFile.getAbsolutePath(),
                            docType,
                            destFile.length(),
                            System.currentTimeMillis(),
                            "Recent"
                    );
                    DocumentRepository.getInstance(this).addDocument(item);

                    runOnUiThread(() -> {
                        if ("EXCEL".equals(docType)) {
                            Intent viewIntent = new Intent(this, ExcelViewerActivity.class);
                            viewIntent.putExtra("FILE_PATH", destFile.getAbsolutePath());
                            startActivity(viewIntent);
                        } else {
                            Intent viewIntent = new Intent(this, PdfViewerActivity.class);
                            viewIntent.putExtra("FILE_PATH", destFile.getAbsolutePath());
                            startActivity(viewIntent);
                        }
                    });
                } catch (Exception ignored) {}
            });
        }
    }

    private static String getFileNameFromUri(Context context, Uri uri) {
        if (uri == null) return "Document_" + System.currentTimeMillis() + ".pdf";
        String name = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (name == null || name.trim().isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    name = path.substring(cut + 1);
                }
            }
        }
        if (name == null || name.trim().isEmpty() || !name.contains(".")) {
            String mimeType = context.getContentResolver().getType(uri);
            String ext = ".pdf";
            if (mimeType != null) {
                if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) {
                    ext = ".docx";
                } else if (mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("csv")) {
                    ext = ".xlsx";
                } else if (mimeType.contains("text")) {
                    ext = ".txt";
                }
            }
            name = (name != null && !name.trim().isEmpty() ? name : "Document_" + System.currentTimeMillis()) + (name != null && name.contains(".") ? "" : ext);
        }
        return name;
    }

    public static void showDonationDialogIfNeeded(Context context, boolean forceShow) {
        SharedPreferences prefs = context.getSharedPreferences("asterdoc_donation_pref", Context.MODE_PRIVATE);
        long lastTime = prefs.getLong("last_donation_time", 0);
        long oneMonthMs = 30L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();

        if (forceShow || (now - lastTime > oneMonthMs)) {
            prefs.edit().putLong("last_donation_time", now).apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.support_author_title));
            builder.setMessage(context.getString(R.string.support_author_msg));
            builder.setPositiveButton(context.getString(R.string.copy_bank_account), (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Bank Account", "20157915001");
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, context.getString(R.string.bank_copied_msg), Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton(context.getString(R.string.close), null);
            builder.show();
        }
    }

    public static void showPrivacyPolicyDialogIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("asterine_privacy_popup_pref", Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean("privacy_shown", false);
        if (!shown) {
            prefs.edit().putBoolean("privacy_shown", true).apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(context.getString(R.string.privacy_policy));
            builder.setIcon(R.drawable.asterine_logo_image);
            builder.setMessage(context.getString(R.string.privacy_policy_message));
            builder.setPositiveButton(context.getString(R.string.close), null);
            builder.show();
        }
    }
}
