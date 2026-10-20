package com.asterdoc.app.ui.faq;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.R;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;

public class FaqActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);

        MaterialToolbar toolbar = findViewById(R.id.toolbarFaq);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }
}
