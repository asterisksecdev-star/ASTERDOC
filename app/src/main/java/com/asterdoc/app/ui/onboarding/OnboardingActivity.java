package com.asterdoc.app.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.R;
import com.asterdoc.app.ui.auth.AuthActivity;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.button.MaterialButton;

public class OnboardingActivity extends AppCompatActivity {

    public static final String PREF_ONBOARDING = "asterdoc_onboarding";
    public static final String KEY_COMPLETED = "completed";

    private int step = 0;
    private TextView tvTitle;
    private TextView tvDesc;
    private MaterialButton btnNext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREF_ONBOARDING, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_COMPLETED, false)) {
            startNextActivity();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        tvTitle = findViewById(R.id.tvOnboardingTitle);
        tvDesc = findViewById(R.id.tvOnboardingDesc);
        btnNext = findViewById(R.id.btnOnboardingNext);

        updateStep();

        btnNext.setOnClickListener(v -> {
            if (step < 3) {
                step++;
                updateStep();
            } else {
                prefs.edit().putBoolean(KEY_COMPLETED, true).apply();
                startNextActivity();
            }
        });
    }

    private void updateStep() {
        String[] titles = {
                getString(R.string.welcome),
                getString(R.string.scan_document),
                getString(R.string.edit_organize),
                getString(R.string.faq_q4)
        };

        String[] descriptions = {
                getString(R.string.app_tagline),
                getString(R.string.tool_scan_sub),
                getString(R.string.convert_scan_sub),
                getString(R.string.faq_a4)
        };

        if (step == 0) {
            tvTitle.setText(titles[0]);
            tvDesc.setText(descriptions[0]);
            btnNext.setText(getString(R.string.btn_next));
            return;
        }

        tvTitle.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
            tvTitle.setText(titles[step]);
            tvTitle.setTranslationX(40f);
            tvTitle.animate().alpha(1f).translationX(0f).setDuration(200).start();
        }).start();

        tvDesc.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
            tvDesc.setText(descriptions[step]);
            tvDesc.setTranslationX(40f);
            tvDesc.animate().alpha(1f).translationX(0f).setDuration(200).start();
        }).start();

        if (step == titles.length - 1) {
            btnNext.setText(getString(R.string.btn_get_started));
        } else {
            btnNext.setText(getString(R.string.btn_next));
        }
    }

    private void startNextActivity() {
        startActivity(new Intent(this, AuthActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
}
