package com.asterdoc.app;

import android.app.Application;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;

import com.asterdoc.app.ads.AdManager;

public class AsterDocApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        if (BuildConfig.DEBUG) {
            Log.d(AdManager.TAG, "ASTERINE_ADMOB: Build type = DEBUG; TEST ADS ENABLED");
        } else {
            Log.d(AdManager.TAG, "ASTERINE_ADMOB: Build type = RELEASE; PRODUCTION ADS ENABLED");
        }

        AdManager.initialize(this);
    }
}
