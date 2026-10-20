package com.asterdoc.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.MainActivity;
import com.asterdoc.app.R;
import com.asterdoc.app.auth.AuthManager;
import com.asterdoc.app.ui.auth.AuthActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivLogo = findViewById(R.id.ivSplashLogo);
        if (ivLogo != null) {
            ivLogo.setScaleX(0.7f);
            ivLogo.setScaleY(0.7f);
            ivLogo.setAlpha(0f);

            ivLogo.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(900)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AuthManager authManager = AuthManager.getInstance(this);
            Intent intent;
            if (authManager.isSessionLocked()) {
                intent = new Intent(SplashActivity.this, AuthActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1300);
    }
}
