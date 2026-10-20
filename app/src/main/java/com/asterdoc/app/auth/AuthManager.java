package com.asterdoc.app.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.util.LocaleHelper;

import java.io.File;

public class AuthManager {
    private static final String PREF_NAME = "asterdoc_auth_pref";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_IS_LOCKED = "is_session_locked";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_WELCOME_COMPLETED = "welcome_completed_";

    private static AuthManager instance;
    private final SharedPreferences prefs;

    private AuthManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public boolean isSessionLocked() {
        return !isLoggedIn();
    }

    public void lockSession() {
        prefs.edit().putBoolean(KEY_IS_LOCKED, true).apply();
    }

    public void unlockSession() {
        prefs.edit()
                .putBoolean(KEY_IS_LOCKED, false)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "user@asterdocs.com");
    }

    public boolean hasCompletedWelcome() {
        String email = getUserEmail();
        return prefs.getBoolean(KEY_WELCOME_COMPLETED + email, false);
    }

    public void setWelcomeCompleted(boolean completed) {
        String email = getUserEmail();
        prefs.edit().putBoolean(KEY_WELCOME_COMPLETED + email, completed).apply();
    }

    public boolean login(String usernameOrEmail, String password) {
        String savedName = prefs.getString(KEY_USER_NAME, null);
        String savedEmail = prefs.getString(KEY_USER_EMAIL, null);
        String savedPassword = prefs.getString(KEY_USER_PASSWORD, null);

        if (usernameOrEmail == null || password == null) return false;
        String cleanUser = usernameOrEmail.trim().toLowerCase();

        boolean isNameMatch = (savedName != null && savedName.trim().toLowerCase().equals(cleanUser))
                || (savedEmail != null && savedEmail.trim().toLowerCase().equals(cleanUser));
        boolean isPasswordMatch = savedPassword != null && savedPassword.equals(password);

        if (isNameMatch && isPasswordMatch) {
            unlockSession();
            return true;
        }
        return false;
    }

    public boolean isAccountRegistered(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return false;
        String cleanId = identifier.trim().toLowerCase();
        String savedName = prefs.getString(KEY_USER_NAME, null);
        String savedEmail = prefs.getString(KEY_USER_EMAIL, null);
        String savedPassword = prefs.getString(KEY_USER_PASSWORD, null);

        if (savedPassword == null && savedName == null && savedEmail == null) {
            return false;
        }

        boolean nameMatch = savedName != null && savedName.trim().toLowerCase().equals(cleanId);
        boolean emailMatch = savedEmail != null && savedEmail.trim().toLowerCase().equals(cleanId);

        return nameMatch || emailMatch;
    }

    public boolean signUp(String username, String email, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.length() < 4) {
            return false;
        }
        prefs.edit()
                .putString(KEY_USER_NAME, username.trim())
                .putString(KEY_USER_EMAIL, email != null ? email.trim().toLowerCase() : "user@asterdocs.com")
                .putString(KEY_USER_PASSWORD, password)
                .apply();
        unlockSession();
        return true;
    }

    public boolean resetPassword(String identifier, String newPassword) {
        if (identifier == null || identifier.trim().isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        if (!isAccountRegistered(identifier)) {
            return false;
        }

        prefs.edit().putString(KEY_USER_PASSWORD, newPassword).apply();
        return true;
    }

    public void logout() {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .putBoolean(KEY_IS_LOCKED, true)
                .apply();
    }

    public void deleteAccount(Context context) {
        Context appContext = context.getApplicationContext();

        try {
            DocumentRepository.getInstance(appContext).clearAllDocuments();
        } catch (Exception ignored) {}

        try {
            String currentLang = LocaleHelper.getLanguage(appContext);

            appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
            appContext.getSharedPreferences("asterdoc_docs_pref", Context.MODE_PRIVATE).edit().clear().apply();
            appContext.getSharedPreferences("asterdoc_billing_pref", Context.MODE_PRIVATE).edit().clear().apply();
            appContext.getSharedPreferences("asterdoc_donation_pref", Context.MODE_PRIVATE).edit().clear().apply();
            appContext.getSharedPreferences("asterdoc_onboarding", Context.MODE_PRIVATE).edit().clear().apply();

            LocaleHelper.setLocale(appContext, currentLang);
        } catch (Exception ignored) {}

        try {
            deleteDir(appContext.getCacheDir());
            File extCache = appContext.getExternalCacheDir();
            if (extCache != null) deleteDir(extCache);
            File importedDir = appContext.getExternalFilesDir("ImportedDocs");
            if (importedDir != null) deleteDir(importedDir);
            File ocrDir = appContext.getExternalFilesDir("OcrNotes");
            if (ocrDir != null) deleteDir(ocrDir);
        } catch (Exception ignored) {}
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDir(new File(dir, child));
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }
}
