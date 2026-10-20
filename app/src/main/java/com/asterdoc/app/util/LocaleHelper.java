package com.asterdoc.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_LANG = "asterdoc_lang";
    private static final String KEY_LANG = "app_lang";

    public static Context onAttach(Context context) {
        String lang = getPersistedData(context, "en");
        return updateResources(context, lang);
    }

    public static String getLanguage(Context context) {
        return getPersistedData(context, "en");
    }

    public static Context setLocale(Context context, String language) {
        persist(context, language);
        try {
            LocaleListCompat appLocales = LocaleListCompat.forLanguageTags(language);
            AppCompatDelegate.setApplicationLocales(appLocales);
        } catch (Exception ignored) {}
        return updateResources(context, language);
    }

    private static String getPersistedData(Context context, String defaultLanguage) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_LANG, Context.MODE_PRIVATE);
        return preferences.getString(KEY_LANG, defaultLanguage);
    }

    private static void persist(Context context, String language) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_LANG, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LANG, language).apply();
    }

    public static Context updateResources(Context context, String language) {
        Locale locale;
        if (language != null && language.contains("-")) {
            String[] parts = language.split("-");
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(language != null ? language : "en");
        }
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        }

        res.updateConfiguration(config, res.getDisplayMetrics());
        Context localizedContext = context.createConfigurationContext(config);

        try {
            Resources appRes = context.getApplicationContext().getResources();
            Configuration appConfig = new Configuration(appRes.getConfiguration());
            appConfig.setLocale(locale);
            appRes.updateConfiguration(appConfig, appRes.getDisplayMetrics());
        } catch (Exception ignored) {}

        return localizedContext;
    }
}
