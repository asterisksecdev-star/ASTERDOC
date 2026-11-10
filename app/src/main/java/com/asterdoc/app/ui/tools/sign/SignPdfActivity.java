package com.asterdoc.app.ui.tools.sign;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.R;
import com.asterdoc.app.data.AsterineStorageManager;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
    private TextView tvChosenSigName;

    private View layoutUploadPhase;
    private View layoutFullScreenEditor;

    private FrameLayout layoutPdfPreviewFrame;
    private ImageView ivPdfPagePreview;
    private ImageView ivInteractiveSignatureOverlay;

    private TextView tvPageNumSign;
    private SeekBar seekBarSigRotate;
    private SeekBar seekBarSigWidth;

    private MaterialButton btnContinueToEditor;
    private MaterialButton btnApplySignature;

    private ParcelFileDescriptor pfd;
    private PdfRenderer pdfRenderer;
    private int currentPageIndex = 0;
    private int totalPdfPages = 0;

    private float currentRotationAngle = 0f;
    private float dX = 0f;
    private float dY = 0f;

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

        layoutUploadPhase = findViewById(R.id.layoutUploadPhase);
        layoutFullScreenEditor = findViewById(R.id.layoutFullScreenEditor);

        layoutPdfPreviewFrame = findViewById(R.id.layoutPdfPreviewFrame);
        ivPdfPagePreview = findViewById(R.id.ivPdfPagePreview);
        ivInteractiveSignatureOverlay = findViewById(R.id.ivInteractiveSignatureOverlay);

        tvPageNumSign = findViewById(R.id.tvPageNumSign);
        seekBarSigRotate = findViewById(R.id.seekBarSigRotate);
        seekBarSigWidth = findViewById(R.id.seekBarSigWidth);

        MaterialButton btnPickSignPdf = findViewById(R.id.btnPickSignPdf);
        MaterialButton btnPickSignatureImage = findViewById(R.id.btnPickSignatureImage);
        btnContinueToEditor = findViewById(R.id.btnContinueToEditor);

        MaterialButton btnPrevPageSign = findViewById(R.id.btnPrevPageSign);
        MaterialButton btnNextPageSign = findViewById(R.id.btnNextPageSign);
        MaterialButton btnRotateCCW = findViewById(R.id.btnRotateCCW);
        MaterialButton btnRotateCW = findViewById(R.id.btnRotateCW);
        MaterialButton btnRemoveSignature = findViewById(R.id.btnRemoveSignature);
        btnApplySignature = findViewById(R.id.btnApplySignature);

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

        btnPrevPageSign.setOnClickListener(v -> {
            if (currentPageIndex > 0) {
                currentPageIndex--;
                renderCurrentPdfPage();
            }
        });

        btnNextPageSign.setOnClickListener(v -> {
            if (currentPageIndex < totalPdfPages - 1) {
                currentPageIndex++;
                renderCurrentPdfPage();
            }
        });

        btnRotateCCW.setOnClickListener(v -> {
            currentRotationAngle = (currentRotationAngle - 15f + 360f) % 360f;
            seekBarSigRotate.setProgress((int) currentRotationAngle);
            ivInteractiveSignatureOverlay.setRotation(currentRotationAngle);
        });

        btnRotateCW.setOnClickListener(v -> {
            currentRotationAngle = (currentRotationAngle + 15f) % 360f;
            seekBarSigRotate.setProgress((int) currentRotationAngle);
            ivInteractiveSignatureOverlay.setRotation(currentRotationAngle);
        });

        seekBarSigRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentRotationAngle = progress;
                ivInteractiveSignatureOverlay.setRotation(currentRotationAngle);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarSigWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOverlayWidth(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnRemoveSignature.setOnClickListener(v -> {
            signatureBitmap = null;
            tvChosenSigName.setText("No signature image uploaded");
            ivInteractiveSignatureOverlay.setVisibility(View.GONE);
            layoutFullScreenEditor.setVisibility(View.GONE);
            layoutUploadPhase.setVisibility(View.VISIBLE);
            updateContinueButtonState();
        });

        btnApplySignature.setOnClickListener(v -> applySignature());

        setupDragTouchListener();

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

        layoutUploadPhase.setVisibility(View.GONE);
        layoutFullScreenEditor.setVisibility(View.VISIBLE);

        loadPdfRendererAndRenderPage();
        setupSignatureOverlay();
    }



    private void setupDragTouchListener() {
        ivInteractiveSignatureOverlay.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    int parentWidth = layoutPdfPreviewFrame.getWidth();
                    int parentHeight = layoutPdfPreviewFrame.getHeight();

                    newX = Math.max(0, Math.min(newX, parentWidth - view.getWidth()));
                    newY = Math.max(0, Math.min(newY, parentHeight - view.getHeight()));

                    view.setX(newX);
                    view.setY(newY);
                    break;

                default:
                    return false;
            }
            return true;
        });
    }

    private void loadPdfRendererAndRenderPage() {
        if (selectedPdfFile == null || !selectedPdfFile.exists()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (pfd != null) pfd.close();
                if (pdfRenderer != null) pdfRenderer.close();

                pfd = ParcelFileDescriptor.open(selectedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pfd);
                totalPdfPages = pdfRenderer.getPageCount();
                currentPageIndex = 0;

                runOnUiThread(this::renderCurrentPdfPage);
            } catch (Exception e) {
                runOnUiThread(() -> BrandedToast.show(this, "Could not load PDF preview."));
            }
        });
    }

    private void renderCurrentPdfPage() {
        if (pdfRenderer == null || totalPdfPages == 0) return;

        tvPageNumSign.setText("Page " + (currentPageIndex + 1) + " of " + totalPdfPages);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                PdfRenderer.Page page = pdfRenderer.openPage(currentPageIndex);
                int width = page.getWidth();
                int height = page.getHeight();

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                runOnUiThread(() -> ivPdfPagePreview.setImageBitmap(bitmap));
            } catch (Exception ignored) {}
        });
    }

    private void setupSignatureOverlay() {
        if (signatureBitmap == null) return;

        ivInteractiveSignatureOverlay.setImageBitmap(signatureBitmap);
        ivInteractiveSignatureOverlay.setVisibility(View.VISIBLE);

        updateOverlayWidth(seekBarSigWidth.getProgress());

        layoutPdfPreviewFrame.post(() -> {
            int centerX = (layoutPdfPreviewFrame.getWidth() - ivInteractiveSignatureOverlay.getWidth()) / 2;
            int centerY = (layoutPdfPreviewFrame.getHeight() - ivInteractiveSignatureOverlay.getHeight()) / 2;
            ivInteractiveSignatureOverlay.setX(Math.max(0, centerX));
            ivInteractiveSignatureOverlay.setY(Math.max(0, centerY));
        });
    }

    private void updateOverlayWidth(int widthProgress) {
        if (signatureBitmap == null) return;

        int targetWidthPx = (int) (widthProgress * getResources().getDisplayMetrics().density * 1.5f);
        targetWidthPx = Math.max(60, targetWidthPx);

        float aspectRatio = (float) signatureBitmap.getHeight() / signatureBitmap.getWidth();
        int targetHeightPx = Math.max(30, (int) (targetWidthPx * aspectRatio));

        ViewGroup.LayoutParams lp = ivInteractiveSignatureOverlay.getLayoutParams();
        lp.width = targetWidthPx;
        lp.height = targetHeightPx;
        ivInteractiveSignatureOverlay.setLayoutParams(lp);
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
            BrandedToast.show(this, "Please upload a signature image first.");
            return;
        }

        btnApplySignature.setEnabled(false);
        BrandedToast.show(this, getString(R.string.msg_prep_doc_view));

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int frameW = layoutPdfPreviewFrame.getWidth();
                int frameH = layoutPdfPreviewFrame.getHeight();

                PdfRenderer.Page page = pdfRenderer.openPage(currentPageIndex);
                int pdfW = page.getWidth();
                int pdfH = page.getHeight();
                page.close();

                float overlayX = ivInteractiveSignatureOverlay.getX();
                float overlayY = ivInteractiveSignatureOverlay.getY();
                float overlayW = ivInteractiveSignatureOverlay.getWidth();

                float pdfAspect = (float) pdfW / pdfH;
                float frameAspect = (float) frameW / frameH;

                float displayedW, displayedH, pageLeft, pageTop;
                if (pdfAspect > frameAspect) {
                    displayedW = frameW;
                    displayedH = frameW / pdfAspect;
                    pageLeft = 0;
                    pageTop = (frameH - displayedH) / 2f;
                } else {
                    displayedH = frameH;
                    displayedW = frameH * pdfAspect;
                    pageLeft = (frameW - displayedW) / 2f;
                    pageTop = 0;
                }

                float relX = overlayX - pageLeft;
                float relY = overlayY - pageTop;

                int pdfX = (int) ((relX / Math.max(1f, displayedW)) * pdfW);
                int pdfY = (int) ((relY / Math.max(1f, displayedH)) * pdfH);
                int pdfSigW = (int) ((overlayW / Math.max(1f, displayedW)) * pdfW);

                File targetDir = AsterineStorageManager.getSubDir(this, AsterineStorageManager.SubFolder.SIGNED);
                String baseName = selectedPdfFile.getName().replaceFirst("\\.[^.]+$", "") + "_signed.pdf";
                File outputFile = AsterineStorageManager.getUniqueFile(targetDir, baseName);

                File signedFile = PdfProcessor.signPdfCustomRotated(
                        this,
                        selectedPdfFile,
                        signatureBitmap,
                        currentPageIndex,
                        pdfX,
                        pdfY,
                        pdfSigW,
                        currentRotationAngle,
                        outputFile.getName()
                );

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
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_bottom);
    }
}

