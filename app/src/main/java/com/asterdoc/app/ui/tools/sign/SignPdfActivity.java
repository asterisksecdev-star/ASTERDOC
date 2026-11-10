package com.asterdoc.app.ui.tools.sign;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.R;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SignPdfActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    private File selectedPdfFile;
    private Bitmap signatureBitmap;
    private TextView tvChosenPdfName;
    private TextView tvChosenSigName;
    private ImageView ivSignaturePreview;
    private MaterialButton btnContinueToEditor;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    selectedPdfFile = copyUriToTempFile(uri);
                    if (selectedPdfFile != null) {
                        tvChosenPdfName.setText(selectedPdfFile.getName());
                        updateContinueButtonState();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> sigPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    try {
                        Uri uri = result.getData().getData();
                        InputStream is = getContentResolver().openInputStream(uri);
                        signatureBitmap = BitmapFactory.decodeStream(is);
                        if (is != null) is.close();

                        if (signatureBitmap != null) {
                            if (ivSignaturePreview != null) {
                                ivSignaturePreview.setImageBitmap(signatureBitmap);
                            }
                            tvChosenSigName.setText("Signature Image Selected ✓");
                            updateContinueButtonState();
                        }
                    } catch (Exception e) {
                        BrandedToast.show(this, getString(R.string.msg_import_error));
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_pdf);

        MaterialToolbar toolbar = findViewById(R.id.toolbarSign);
        toolbar.setTitle(R.string.sign_pdf_title);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvChosenPdfName = findViewById(R.id.tvChosenPdfName);
        tvChosenSigName = findViewById(R.id.tvChosenSigName);
        ivSignaturePreview = findViewById(R.id.ivSignaturePreview);

        MaterialButton btnPickSignPdf = findViewById(R.id.btnPickSignPdf);
        MaterialButton btnPickSignatureImage = findViewById(R.id.btnPickSignatureImage);
        btnContinueToEditor = findViewById(R.id.btnContinueToEditor);

        btnPickSignPdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            pdfPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.btn_choose_pdf)));
        });

        btnPickSignatureImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            sigPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.btn_choose_sig_img)));
        });

        if (btnContinueToEditor != null) {
            btnContinueToEditor.setOnClickListener(v -> openFullScreenEditor());
        }

        String preloadPath = getIntent().getStringExtra("PRELOAD_FILE_PATH");
        if (preloadPath != null) {
            File f = new File(preloadPath);
            if (f.exists()) {
                selectedPdfFile = f;
                tvChosenPdfName.setText(f.getName());
                updateContinueButtonState();
            }
        }
    }

    private void updateContinueButtonState() {
        if (btnContinueToEditor != null) {
            btnContinueToEditor.setEnabled(selectedPdfFile != null && selectedPdfFile.exists() && signatureBitmap != null);
        }
    }

    private void openFullScreenEditor() {
        if (selectedPdfFile == null || !selectedPdfFile.exists() || signatureBitmap == null) return;

        try {
            File sigFile = File.createTempFile("temp_sig", ".png", getCacheDir());
            FileOutputStream fos = new FileOutputStream(sigFile);
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            Intent intent = new Intent(this, SignatureEditorActivity.class);
            intent.putExtra(SignatureEditorActivity.EXTRA_PDF_PATH, selectedPdfFile.getAbsolutePath());
            intent.putExtra(SignatureEditorActivity.EXTRA_SIG_PATH, sigFile.getAbsolutePath());
            startActivity(intent);
        } catch (Exception e) {
            BrandedToast.show(this, "Could not open placement window: " + e.getMessage());
        }
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File tempFile = File.createTempFile("sign_pdf", ".pdf", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            is.close();
            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }
}
