package com.asterdoc.app.ads;

import android.content.Context;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public class AdManager {
    private static boolean initialized = false;

    public static void initialize(Context context) {
        if (!initialized) {
            MobileAds.initialize(context, initializationStatus -> {
                initialized = true;
            });
        }
    }

    public static void loadBannerAd(Context context, ViewGroup container, boolean isPro) {
        if (isPro) {
            container.setVisibility(ViewGroup.GONE);
            return;
        }
        initialize(context);
        container.setVisibility(ViewGroup.VISIBLE);
        if (container.getChildCount() == 0) {
            AdView adView = new AdView(context);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId("ca-app-pub-3940256099942544/6300978111"); // Sample test ad unit ID
            container.addView(adView);
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        }
    }
}
