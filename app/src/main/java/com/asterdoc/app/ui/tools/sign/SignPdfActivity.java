package com.asterdoc.app.ui.tools.sign;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class SignPdfActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    private File selectedPdfFile;
    private Bitmap signatureBitmap;
    private TextView tvChosenPdfName;
    private ImageView ivSignaturePreview;
    private SeekBar seekBarSigWidth;
    private SeekBar seekBarSigX;
    private SeekBar seekBarSigY;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    selectedPdfFile = copyUriToTempFile(uri);
                    if (selectedPdfFile != null) {
                        tvChosenPdfName.setText(selectedPdfFile.getName());
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
                            ivSignaturePreview.setImageBitmap(signatureBitmap);
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
        ivSignaturePreview = findViewById(R.id.ivSignaturePreview);
        seekBarSigWidth = findViewById(R.id.seekBarSigWidth);
        seekBarSigX = findViewById(R.id.seekBarSigX);
        seekBarSigY = findViewById(R.id.seekBarSigY);

        MaterialButton btnPickSignPdf = findViewById(R.id.btnPickSignPdf);
        MaterialButton btnPickSignatureImage = findViewById(R.id.btnPickSignatureImage);
        MaterialButton btnDrawSignature = findViewById(R.id.btnDrawSignature);
        MaterialButton btnApplySignature = findViewById(R.id.btnApplySignature);

        createDefaultSignatureBitmap();

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

        btnDrawSignature.setOnClickListener(v -> {
            createDefaultSignatureBitmap();
            BrandedToast.show(this, getString(R.string.btn_draw_sig));
        });

        btnApplySignature.setOnClickListener(v -> applySignature());

        String preloadPath = getIntent().getStringExtra("PRELOAD_FILE_PATH");
        if (preloadPath != null) {
            File f = new File(preloadPath);
            if (f.exists()) {
                selectedPdfFile = f;
                tvChosenPdfName.setText(f.getName());
            }
        }
    }

    private void createDefaultSignatureBitmap() {
        signatureBitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(signatureBitmap);
        canvas.drawColor(Color.TRANSPARENT);
        Paint paint = new Paint();
        paint.setColor(0xFF1A73E8);
        paint.setTextSize(32f);
        paint.setFakeBoldText(true);
        canvas.drawText("Verified Signature", 20, 65, paint);
        ivSignaturePreview.setImageBitmap(signatureBitmap);
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

    private void applySignature() {
        if (selectedPdfFile == null || !selectedPdfFile.exists()) {
            BrandedToast.show(this, getString(R.string.btn_choose_pdf));
            return;
        }
        if (signatureBitmap == null) {
            BrandedToast.show(this, getString(R.string.btn_choose_sig_img));
            return;
        }

        MaterialButton btnApplySignature = findViewById(R.id.btnApplySignature);
        btnApplySignature.setEnabled(false);
        BrandedToast.show(this, getString(R.string.msg_prep_doc_view));

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int customWidth = seekBarSigWidth.getProgress() + 40;
                int posX = seekBarSigX.getProgress();
                int posY = seekBarSigY.getProgress();

                File signedFile = PdfProcessor.signPdfCustom(this, selectedPdfFile, signatureBitmap, 0, posX, posY, customWidth, "Signed_Document_" + System.currentTimeMillis() + ".pdf");

                DocumentItem item = new DocumentItem(
                        String.valueOf(System.currentTimeMillis()),
                        signedFile.getName(),
                        signedFile.getAbsolutePath(),
                        "PDF",
                        signedFile.length(),
                        System.currentTimeMillis(),
                        "Created"
                );
                DocumentRepository.getInstance(this).addDocument(item);

                runOnUiThread(() -> {
                    btnApplySignature.setEnabled(true);
                    BrandedToast.show(this, getString(R.string.msg_processing_complete));

                    Intent intent = new Intent(this, PdfViewerActivity.class);
                    intent.putExtra("FILE_PATH", signedFile.getAbsolutePath());
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnApplySignature.setEnabled(true);
                    BrandedToast.show(this, getString(R.string.msg_import_error) + " " + e.getMessage());
                });
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_bottom);
    }
}
