package com.asterdoc.app.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.asterdoc.app.BuildConfig;
import com.asterdoc.app.R;
import com.asterdoc.app.util.BrandedToast;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

public class AdManager {
    public static final String TAG = "ASTERINE_ADMOB";
    private static boolean initialized = false;

    private static InterstitialAd mInterstitialAd = null;
    private static boolean isLoadingInterstitial = false;
    private static long lastInterstitialShownTime = 0;
    private static final long FREQUENCY_CAP_MS = 5 * 60 * 1000; // 5 minutes

    public static void initialize(@NonNull Context context) {
        if (!initialized) {
            Log.d(TAG, "ASTERINE_ADMOB: SDK initialization started.");
            MobileAds.initialize(context, initializationStatus -> {
                initialized = true;
                Log.d(TAG, "ASTERINE_ADMOB: SDK initialization completed successfully.");
                preloadInterstitial(context);
            });
        }
    }

    public static void preloadInterstitial(@NonNull Context context) {
        if (mInterstitialAd != null || isLoadingInterstitial) {
            return;
        }
        isLoadingInterstitial = true;
        String adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID;
        Log.d(TAG, "ASTERINE_ADMOB: Interstitial preload requested. Unit ID: " + adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, adUnitId, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                mInterstitialAd = interstitialAd;
                isLoadingInterstitial = false;
                Log.d(TAG, "ASTERINE_ADMOB: Interstitial successfully loaded and cached.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mInterstitialAd = null;
                isLoadingInterstitial = false;
                Log.e(TAG, "ASTERINE_ADMOB: Interstitial load failed. Error Code: "
                        + loadAdError.getCode() + ", Message: " + loadAdError.getMessage());
            }
        });
    }

    public static void showInterstitial(@NonNull Activity activity, @NonNull String toolName, @NonNull Runnable onComplete) {
        Log.d(TAG, "ASTERINE_ADMOB: Tool clicked: " + toolName);

        long now = System.currentTimeMillis();
        if (lastInterstitialShownTime > 0 && (now - lastInterstitialShownTime < FREQUENCY_CAP_MS)) {
            Log.d(TAG, "ASTERINE_ADMOB: Frequency cap active (less than 5 mins since last ad). Opening tool: " + toolName);
            onComplete.run();
            return;
        }

        if (mInterstitialAd != null) {
            Log.d(TAG, "ASTERINE_ADMOB: Interstitial display requested for tool: " + toolName);
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "ASTERINE_ADMOB: Interstitial actually displayed (onAdShowedFullScreenContent)");
                    lastInterstitialShownTime = System.currentTimeMillis();
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "ASTERINE_ADMOB: Interstitial dismissed. Opening selected tool: " + toolName);
                    mInterstitialAd = null;
                    preloadInterstitial(activity);
                    onComplete.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                    Log.e(TAG, "ASTERINE_ADMOB: Interstitial failed to show. Error: " + adError.getMessage());
                    mInterstitialAd = null;
                    preloadInterstitial(activity);
                    onComplete.run();
                }
            });

            mInterstitialAd.show(activity);
        } else {
            Log.d(TAG, "ASTERINE_ADMOB: Interstitial not preloaded yet. Loading on-demand for tool: " + toolName);
            String adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID;
            AdRequest adRequest = new AdRequest.Builder().build();

            BrandedToast.show(activity, "Loading ad...");

            InterstitialAd.load(activity, adUnitId, adRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    Log.d(TAG, "ASTERINE_ADMOB: Interstitial loaded successfully");
                    mInterstitialAd = interstitialAd;
                    Log.d(TAG, "ASTERINE_ADMOB: Interstitial display requested");
                    mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdShowedFullScreenContent() {
                            Log.d(TAG, "ASTERINE_ADMOB: Interstitial displayed successfully");
                            lastInterstitialShownTime = System.currentTimeMillis();
                        }

                        @Override
                        public void onAdDismissedFullScreenContent() {
                            Log.d(TAG, "ASTERINE_ADMOB: Interstitial dismissed");
                            mInterstitialAd = null;
                            preloadInterstitial(activity);
                            Log.d(TAG, "ASTERINE_ADMOB: Opening selected tool: " + toolName);
                            onComplete.run();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            Log.e(TAG, "ASTERINE_ADMOB: Interstitial failed to show. Error: " + adError.getMessage());
                            mInterstitialAd = null;
                            preloadInterstitial(activity);
                            onComplete.run();
                        }
                    });
                    mInterstitialAd.show(activity);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.e(TAG, "ASTERINE_ADMOB: Interstitial load failed. Error Code: "
                            + loadAdError.getCode() + ", Message: " + loadAdError.getMessage());
                    Log.d(TAG, "ASTERINE_ADMOB: Opening selected tool: " + toolName);
                    onComplete.run();
                }
            });
        }
    }

    public static void loadBannerAd(@NonNull Context context, ViewGroup container) {
        if (container == null) return;

        initialize(context);
        container.setVisibility(ViewGroup.VISIBLE);

        if (container.getChildCount() > 0) return;

        try {
            String adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID;
            Log.d(TAG, "ASTERINE_ADMOB: Selected banner ID: " + adUnitId);

            AdView adView = new AdView(context);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId(adUnitId);

            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "ASTERINE_ADMOB: Banner loaded successfully!");
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.e(TAG, "ASTERINE_ADMOB: Banner failed to load. Code: " + loadAdError.getCode() + ", Msg: " + loadAdError.getMessage());
                }
            });

            container.addView(adView);
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            Log.e(TAG, "Exception loading banner ad", e);
        }
    }

    public static void loadNativeAd(@NonNull Context context, @NonNull ViewGroup container) {
        initialize(context);
        container.setVisibility(ViewGroup.VISIBLE);
        if (container.getChildCount() > 0) return;

        String adUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID;
        Log.d(TAG, "ASTERINE_ADMOB: Native ad load requested. Unit ID: " + adUnitId);

        AdLoader adLoader = new AdLoader.Builder(context, adUnitId)
                .forNativeAd(nativeAd -> {
                    Log.d(TAG, "ASTERINE_ADMOB: Native ad loaded successfully.");
                    NativeAdView adView = (NativeAdView) LayoutInflater.from(context)
                            .inflate(R.layout.item_native_ad, container, false);
                    populateNativeAdView(nativeAd, adView);
                    container.removeAllViews();
                    container.addView(adView);
                    Log.d(TAG, "ASTERINE_ADMOB: NativeAdView attached to visible container.");
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "ASTERINE_ADMOB: Native ad failed to load. Error Code: "
                                + loadAdError.getCode() + ", Message: " + loadAdError.getMessage());
                    }
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private static void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));
        adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

        if (nativeAd.getHeadline() != null && adView.getHeadlineView() != null) {
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        }
        if (nativeAd.getBody() != null && adView.getBodyView() != null) {
            ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            adView.getBodyView().setVisibility(View.VISIBLE);
        }
        if (nativeAd.getCallToAction() != null && adView.getCallToActionView() != null) {
            ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            adView.getCallToActionView().setVisibility(View.VISIBLE);
        }
        if (nativeAd.getIcon() != null && adView.getIconView() != null) {
            ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
            adView.getIconView().setVisibility(View.VISIBLE);
        }
        if (nativeAd.getAdvertiser() != null && adView.getAdvertiserView() != null) {
            ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
            adView.getAdvertiserView().setVisibility(View.VISIBLE);
        }
        adView.setNativeAd(nativeAd);
    }
}
