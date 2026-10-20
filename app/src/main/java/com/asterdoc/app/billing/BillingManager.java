package com.asterdoc.app.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class BillingManager {

    private static final String PREF_NAME = "asterdoc_billing_pref";
    private static final String KEY_IS_PRO = "is_pro_user";
    private static final String KEY_USER_TIER = "user_access_tier";

    public static final String DEV_SECRET_CODE = "BITSTUDENT@2026";

    public static final String SKU_MONTHLY = "asterdoc_sub_monthly";
    public static final String SKU_YEARLY = "asterdoc_sub_yearly";
    public static final String SKU_LIFETIME = "asterdoc_inapp_lifetime";

    public enum UserTier {
        FREE("Full Version — Unlocked"),
        PLAY_PREMIUM("Full Version — Unlocked"),
        TESTER_PREMIUM("Full Version — Unlocked"),
        INTERNAL_DEV("Full Version — Unlocked");

        private final String label;

        UserTier(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static BillingManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;

    private BillingManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_IS_PRO, true)
                .putString(KEY_USER_TIER, UserTier.FREE.name())
                .apply();
    }

    public static synchronized BillingManager getInstance(Context context) {
        if (instance == null) {
            instance = new BillingManager(context);
        }
        return instance;
    }

    public static boolean isToolFree(String toolType) {
        return true;
    }

    public void connectToGooglePlay() {
        // All features unlocked; no connection needed
    }

    public void launchPlayBilling(Activity activity, String productId, String productType) {
        launchPlayBilling(activity, productId, productType, null);
    }

    public void launchPlayBilling(Activity activity, String productId, String productType, PaywallDialog.OnProUnlockedListener listener) {
        if (listener != null) {
            listener.onProUnlocked();
        }
    }

    public boolean isPro() {
        return true;
    }

    public void setUserAccess(boolean isPro, UserTier tier) {
        prefs.edit()
                .putBoolean(KEY_IS_PRO, true)
                .putString(KEY_USER_TIER, UserTier.FREE.name())
                .apply();
    }

    public void setPro(boolean isPro) {
        setUserAccess(true, UserTier.FREE);
    }

    public UserTier getUserTier() {
        return UserTier.FREE;
    }

    public boolean redeemDeveloperCode(String code) {
        return true;
    }
}
