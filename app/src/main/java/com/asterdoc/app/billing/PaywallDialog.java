package com.asterdoc.app.billing;

import android.content.Context;

public class PaywallDialog {

    public interface OnProUnlockedListener {
        void onProUnlocked();
    }

    public static void showPaywall(Context context, OnProUnlockedListener listener) {
        if (listener != null) {
            listener.onProUnlocked();
        }
    }

    public static void showDeveloperCodeDialog(Context context, OnProUnlockedListener listener) {
        if (listener != null) {
            listener.onProUnlocked();
        }
    }
}
