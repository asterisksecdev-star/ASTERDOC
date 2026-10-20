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
import com.asterdoc.app.auth.AuthManager;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.ui.auth.AuthActivity;
import com.asterdoc.app.ui.files.FilesFragment;
import com.asterdoc.app.ui.home.HomeFragment;
import com.asterdoc.app.ui.onboarding.OnboardingActivity;
import com.asterdoc.app.ui.settings.SettingsFragment;
import com.asterdoc.app.ui.tools.ToolsFragment;
import com.asterdoc.app.ui.viewer.ExcelViewerActivity;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

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

        if (AuthManager.getInstance(this).isSessionLocked()) {
            Intent intent = new Intent(this, AuthActivity.class);
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

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.navigation_files) {
                selectedFragment = new FilesFragment();
            } else if (itemId == R.id.navigation_tools) {
                selectedFragment = new ToolsFragment();
            } else if (itemId == R.id.navigation_settings) {
                selectedFragment = new SettingsFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        showDonationDialogIfNeeded(this, false);

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
                File file = getFileFromUri(this, uri);
                if (file != null && file.exists()) {
                    String name = file.getName().toLowerCase();
                    DocumentItem item = new DocumentItem(
                            String.valueOf(System.currentTimeMillis()),
                            file.getName(),
                            file.getAbsolutePath(),
                            (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) ? "EXCEL" : "PDF",
                            file.length(),
                            System.currentTimeMillis(),
                            "Recent"
                    );
                    DocumentRepository.getInstance(this).addDocument(item);

                    runOnUiThread(() -> {
                        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                            Intent viewIntent = new Intent(this, ExcelViewerActivity.class);
                            viewIntent.putExtra("FILE_PATH", file.getAbsolutePath());
                            startActivity(viewIntent);
                        } else {
                            Intent viewIntent = new Intent(this, PdfViewerActivity.class);
                            viewIntent.setData(uri);
                            viewIntent.putExtra("FILE_PATH", file.getAbsolutePath());
                            startActivity(viewIntent);
                        }
                    });
                }
            });
        }
    }

    private static File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            String fileName = "document_" + System.currentTimeMillis();
            String extension = ".pdf";

            if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String name = cursor.getString(nameIndex);
                            if (name != null) {
                                fileName = name;
                                int dot = name.lastIndexOf('.');
                                if (dot != -1) {
                                    extension = name.substring(dot);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else if (uri.getPath() != null) {
                File pathFile = new File(uri.getPath());
                fileName = pathFile.getName();
                int dot = fileName.lastIndexOf('.');
                if (dot != -1) {
                    extension = fileName.substring(dot);
                }
            }

            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            File tempFile = File.createTempFile("app_open_", extension, context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            is.close();

            return tempFile;
        } catch (Exception e) {
            return null;
        }
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
}
