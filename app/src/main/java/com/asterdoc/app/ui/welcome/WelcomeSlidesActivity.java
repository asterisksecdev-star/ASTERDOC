package com.asterdoc.app.ui.welcome;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.MainActivity;
import com.asterdoc.app.R;
import com.asterdoc.app.auth.AuthManager;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.button.MaterialButton;

public class WelcomeSlidesActivity extends AppCompatActivity {

    private int step = 0;
    private String userName;
    private AuthManager authManager;

    private TextView tvWelcomeStep;
    private TextView tvSlideTitle;
    private TextView tvSlideSubtitle;
    private TextView tvSlideBody;
    private MaterialButton btnWelcomeNext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authManager = AuthManager.getInstance(this);

        setContentView(R.layout.activity_welcome_slides);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.trim().isEmpty()) {
            userName = authManager.getUserName();
        }

        tvWelcomeStep = findViewById(R.id.tvWelcomeStep);
        tvSlideTitle = findViewById(R.id.tvSlideTitle);
        tvSlideSubtitle = findViewById(R.id.tvSlideSubtitle);
        tvSlideBody = findViewById(R.id.tvSlideBody);
        btnWelcomeNext = findViewById(R.id.btnWelcomeNext);

        updateSlideContent();

        btnWelcomeNext.setOnClickListener(v -> {
            if (step < 2) {
                step++;
                updateSlideContentWithAnimation();
            } else {
                authManager.setWelcomeCompleted(true);
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            }
        });
    }

    private void updateSlideContentWithAnimation() {
        tvSlideTitle.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
            updateSlideContent();
            tvSlideTitle.setTranslationX(40f);
            tvSlideTitle.animate().alpha(1f).translationX(0f).setDuration(200).start();
        }).start();

        tvSlideSubtitle.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
            tvSlideSubtitle.setTranslationX(40f);
            tvSlideSubtitle.animate().alpha(1f).translationX(0f).setDuration(200).start();
        }).start();

        tvSlideBody.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
            tvSlideBody.setTranslationX(40f);
            tvSlideBody.animate().alpha(1f).translationX(0f).setDuration(200).start();
        }).start();
    }

    private void updateSlideContent() {
        tvWelcomeStep.setText(getString(R.string.onboarding_step_format, (step + 1)));

        switch (step) {
            case 0:
                tvSlideTitle.setText(getString(R.string.welcome_title_1, userName));
                tvSlideSubtitle.setText(R.string.welcome_subtitle_1);
                tvSlideBody.setText(R.string.welcome_body_1);
                btnWelcomeNext.setText(R.string.btn_next);
                break;
            case 1:
                tvSlideTitle.setText(R.string.welcome_title_2);
                tvSlideSubtitle.setText(R.string.welcome_subtitle_2);
                tvSlideBody.setText(R.string.welcome_body_2);
                btnWelcomeNext.setText(R.string.btn_next);
                break;
            case 2:
                tvSlideTitle.setText(R.string.welcome_title_3);
                tvSlideSubtitle.setText(R.string.welcome_subtitle_3);
                tvSlideBody.setText(R.string.welcome_body_3);
                btnWelcomeNext.setText(R.string.btn_go_dashboard);
                break;
        }
    }
}
