package com.asterdoc.app;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

import com.asterdoc.app.util.LocaleHelper;

public class AsterDocApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        LocaleHelper.onAttach(this);
    }
}
