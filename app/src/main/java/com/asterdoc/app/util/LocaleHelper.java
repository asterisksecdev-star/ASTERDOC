package com.asterdoc.app.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public class LocaleHelper {

    public static Context onAttach(Context context) {
        return updateResources(context, "en");
    }

    public static String getLanguage(Context context) {
        return "en";
    }

    public static Context setLocale(Context context, String language) {
        return updateResources(context, "en");
    }

    public static Context updateResources(Context context, String language) {
        Locale locale = Locale.ENGLISH;
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
        return context.createConfigurationContext(config);
    }
}

