package com.asterdoc.app.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.asterdoc.app.R;

public class BrandedToast {
    public static void show(Context context, String message) {
        if (context == null) return;
        try {
            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.toast_branded, null);
            ImageView imgLogo = layout.findViewById(R.id.toastLogo);
            TextView tvMessage = layout.findViewById(R.id.toastText);
            if (imgLogo != null) imgLogo.setImageResource(R.mipmap.ic_launcher);
            if (tvMessage != null) tvMessage.setText(message);

            Toast toast = new Toast(context);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(layout);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 100);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}
