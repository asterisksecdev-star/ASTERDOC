package com.asterdoc.app.ui.tools.sign;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.asterdoc.app.R;
import com.asterdoc.app.data.AsterineStorageManager;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SignatureEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "EXTRA_PDF_PATH";
    public static final String EXTRA_SIG_PATH = "EXTRA_SIG_PATH";

    private File selectedPdfFile;
    private File selectedSigFile;
    private Bitmap signatureBitmap;

    private FrameLayout layoutPdfPreviewFrame;
    private ImageView ivPdfPagePreview;
    private ImageView ivInteractiveSignatureOverlay;

    private TextView tvPageNumSign;
    private TextView tvRotateLabel;
    private TextView tvSizeLabel;

    private SeekBar seekBarSigRotate;
    private SeekBar seekBarSigWidth;

    private MaterialButton btnApplySignature;

    private ParcelFileDescriptor pfd;
    private PdfRenderer pdfRenderer;
    private int currentPageIndex = 0;
    private int totalPdfPages = 0;

    private float currentRotationAngle = 0f;
    private float dX = 0f;
    private float dY = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_editor);

        MaterialToolbar toolbar = findViewById(R.id.toolbarEditor);
        toolbar.setTitle("Place Your Signature");
        toolbar.setNavigationOnClickListener(v -> finish());

        String pdfPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        String sigPath = getIntent().getStringExtra(EXTRA_SIG_PATH);

        if (pdfPath != null) selectedPdfFile = new File(pdfPath);
        if (sigPath != null) selectedSigFile = new File(sigPath);

        if (selectedPdfFile == null || !selectedPdfFile.exists() || selectedSigFile == null || !selectedSigFile.exists()) {
            BrandedToast.show(this, "Missing PDF or signature file");
            finish();
            return;
        }

        try {
            signatureBitmap = BitmapFactory.decodeFile(selectedSigFile.getAbsolutePath());
        } catch (Exception e) {
            signatureBitmap = null;
        }

        if (signatureBitmap == null) {
            BrandedToast.show(this, "Invalid signature image");
            finish();
            return;
        }

        initViews();
        loadPdfRendererAndRenderPage();
        setupSignatureOverlay();
    }

    private void initViews() {
        layoutPdfPreviewFrame = findViewById(R.id.layoutPdfPreviewFrame);
        ivPdfPagePreview = findViewById(R.id.ivPdfPagePreview);
        ivInteractiveSignatureOverlay = findViewById(R.id.ivInteractiveSignatureOverlay);

        tvPageNumSign = findViewById(R.id.tvPageNumSign);
        tvRotateLabel = findViewById(R.id.tvRotateLabel);
        tvSizeLabel = findViewById(R.id.tvSizeLabel);

        seekBarSigRotate = findViewById(R.id.seekBarSigRotate);
        seekBarSigWidth = findViewById(R.id.seekBarSigWidth);

        MaterialButton btnPrevPageSign = findViewById(R.id.btnPrevPageSign);
        MaterialButton btnNextPageSign = findViewById(R.id.btnNextPageSign);
        MaterialButton btnRotateCCW = findViewById(R.id.btnRotateCCW);
        MaterialButton btnRotateCW = findViewById(R.id.btnRotateCW);
        btnApplySignature = findViewById(R.id.btnApplySignature);

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
            if (tvRotateLabel != null) tvRotateLabel.setText("Rotate: " + (int) currentRotationAngle + "°");
        });

        btnRotateCW.setOnClickListener(v -> {
            currentRotationAngle = (currentRotationAngle + 15f) % 360f;
            seekBarSigRotate.setProgress((int) currentRotationAngle);
            ivInteractiveSignatureOverlay.setRotation(currentRotationAngle);
            if (tvRotateLabel != null) tvRotateLabel.setText("Rotate: " + (int) currentRotationAngle + "°");
        });

        seekBarSigRotate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentRotationAngle = progress;
                ivInteractiveSignatureOverlay.setRotation(currentRotationAngle);
                if (tvRotateLabel != null) tvRotateLabel.setText("Rotate: " + progress + "°");
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

        btnApplySignature.setOnClickListener(v -> applySignature());

        setupDragTouchListener();
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

        int percentage = Math.max(10, (int) (widthProgress / 1.2f));
        if (tvSizeLabel != null) {
            tvSizeLabel.setText("Size: " + percentage + "%");
        }

        int targetWidthPx = (int) (widthProgress * getResources().getDisplayMetrics().density * 1.5f);
        targetWidthPx = Math.max(60, targetWidthPx);

        float aspectRatio = (float) signatureBitmap.getHeight() / signatureBitmap.getWidth();
        int targetHeightPx = Math.max(30, (int) (targetWidthPx * aspectRatio));

        ViewGroup.LayoutParams lp = ivInteractiveSignatureOverlay.getLayoutParams();
        lp.width = targetWidthPx;
        lp.height = targetHeightPx;
        ivInteractiveSignatureOverlay.setLayoutParams(lp);
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
                    showSignedResultBottomSheet(signedFile);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnApplySignature.setEnabled(true);
                    BrandedToast.show(this, getString(R.string.msg_import_error) + " " + e.getMessage());
                });
            }
        });
    }

    private void showSignedResultBottomSheet(File signedFile) {
        if (signedFile == null || !signedFile.exists() || signedFile.length() == 0) {
            BrandedToast.show(this, getString(R.string.msg_import_error));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_signed_result, null);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        TextView tvFileName = view.findViewById(R.id.tvResultFileName);
        TextView tvFileSize = view.findViewById(R.id.tvResultFileSize);
        MaterialButton btnOpen = view.findViewById(R.id.btnOpenSignedPdf);
        MaterialButton btnShare = view.findViewById(R.id.btnShareSignedPdf);
        MaterialButton btnReturn = view.findViewById(R.id.btnReturnAsterine);

        if (tvFileName != null) tvFileName.setText(signedFile.getName());
        if (tvFileSize != null) {
            long bytes = signedFile.length();
            String formattedSize = bytes < 1024 ? bytes + " B" : String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
            tvFileSize.setText(formattedSize + " • Saved to Asterine/Signed");
        }

        if (btnOpen != null) {
            btnOpen.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(this, PdfViewerActivity.class);
                intent.putExtra("FILE_PATH", signedFile.getAbsolutePath());
                startActivity(intent);
                finish();
            });
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/pdf");
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", signedFile);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share Signed PDF"));
                } catch (Exception e) {
                    BrandedToast.show(this, "Could not share file: " + e.getMessage());
                }
            });
        }

        if (btnReturn != null) {
            btnReturn.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
        }

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {}
    }
}
