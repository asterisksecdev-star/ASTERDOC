package com.asterdoc.app.ui.viewer;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.asterdoc.app.R;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.data.AsterineStorageManager;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.tools.ToolActionActivity;
import com.asterdoc.app.ui.tools.sign.SignPdfActivity;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfViewerActivity extends AppCompatActivity implements PdfPageAdapter.OnPageClickListener {

    private File pdfFile;
    private RecyclerView rvPdfPages;
    private PdfPageAdapter pageAdapter;
    private LinearLayoutManager layoutManager;

    private View layoutTopBarContainer;
    private View btnTopBack, btnTopRotate, btnTopInfo, btnTopShare, btnTopMenu;

    private LinearLayout layoutVerticalScrollPill;
    private LinearLayout layoutPageCounterPill;
    private TextView tvPageBadgeCounter;

    private View fabEditDocument;

    private MaterialCardView cardEditControls;
    private ImageButton btnToolPen;
    private ImageButton btnToolHighlighter;
    private ImageButton btnToolEraser;
    private View btnColorRed, btnColorBlue, btnColorBlack, btnColorGreen, btnColorYellow;
    private ImageView btnUndoDraw, btnRedoDraw, btnClearDraw;
    private View btnCancelEditMode, btnDoneEditMode;

    private FloatingActionButton fabToggleTools;

    private TextView tvZoomToast;
    private Runnable zoomToastRunnable;

    private File sourceWordFile = null;
    private String sourceExtension = ".pdf";

    private boolean isUiVisible = true;
    private boolean isEditMode = false;
    private int selectedColor = Color.RED;
    private DrawingCanvasView.ToolMode selectedTool = DrawingCanvasView.ToolMode.PEN;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Handler floatingControlsHandler = new Handler(Looper.getMainLooper());
    private final Runnable floatingControlsRunnable = this::hideFloatingControlsAnimated;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_pdf_viewer);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isEditMode) {
                    exitEditModeWithConfirmation();
                } else {
                    finish();
                }
            }
        });

        String filePath = getIntent().getStringExtra("FILE_PATH");
        Uri dataUri = getIntent().getData();

        if (filePath != null) {
            File targetFile = new File(filePath);
            String name = targetFile.getName().toLowerCase();
            if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                Intent excelIntent = new Intent(this, ExcelViewerActivity.class);
                excelIntent.putExtra("FILE_PATH", targetFile.getAbsolutePath());
                startActivity(excelIntent);
                finish();
                return;
            }
            processAndLoadFile(targetFile, false);
        } else if (dataUri != null) {
            File tempFile = getFileFromUri(dataUri);
            if (tempFile != null) {
                String name = tempFile.getName().toLowerCase();
                if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                    Intent excelIntent = new Intent(this, ExcelViewerActivity.class);
                    excelIntent.putExtra("FILE_PATH", tempFile.getAbsolutePath());
                    startActivity(excelIntent);
                    finish();
                    return;
                }
                processAndLoadFile(tempFile, true);
            } else {
                Toast.makeText(this, "Could not read document URI.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showFloatingControlsAndResetTimer() {
        if (isEditMode) return;
        floatingControlsHandler.removeCallbacks(floatingControlsRunnable);

        if (layoutVerticalScrollPill != null) {
            if (layoutVerticalScrollPill.getVisibility() != View.VISIBLE) {
                layoutVerticalScrollPill.setVisibility(View.VISIBLE);
                layoutVerticalScrollPill.setAlpha(0f);
            }
            layoutVerticalScrollPill.animate().cancel();
            layoutVerticalScrollPill.animate().alpha(1.0f).setDuration(200).start();
        }
        if (layoutPageCounterPill != null) {
            if (layoutPageCounterPill.getVisibility() != View.VISIBLE) {
                layoutPageCounterPill.setVisibility(View.VISIBLE);
                layoutPageCounterPill.setAlpha(0f);
            }
            layoutPageCounterPill.animate().cancel();
            layoutPageCounterPill.animate().alpha(1.0f).setDuration(200).start();
        }
        if (fabEditDocument != null) {
            if (fabEditDocument.getVisibility() != View.VISIBLE) {
                fabEditDocument.setVisibility(View.VISIBLE);
                fabEditDocument.setAlpha(0f);
            }
            fabEditDocument.animate().cancel();
            fabEditDocument.animate().alpha(1.0f).setDuration(200).start();
        }

        floatingControlsHandler.postDelayed(floatingControlsRunnable, 4000);
    }

    private void hideFloatingControlsAnimated() {
        if (isEditMode) return;

        if (layoutVerticalScrollPill != null && layoutVerticalScrollPill.getVisibility() == View.VISIBLE) {
            layoutVerticalScrollPill.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                if (layoutVerticalScrollPill != null) layoutVerticalScrollPill.setVisibility(View.GONE);
            }).start();
        }
        if (layoutPageCounterPill != null && layoutPageCounterPill.getVisibility() == View.VISIBLE) {
            layoutPageCounterPill.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                if (layoutPageCounterPill != null) layoutPageCounterPill.setVisibility(View.GONE);
            }).start();
        }
        if (fabEditDocument != null && fabEditDocument.getVisibility() == View.VISIBLE) {
            fabEditDocument.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                if (fabEditDocument != null) fabEditDocument.setVisibility(View.GONE);
            }).start();
        }
    }

    private void processAndLoadFile(File file, boolean isFromUri) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIcon(R.drawable.asterine_logo_image);
        progressDialog.setTitle("Asterine Reader");
        progressDialog.setMessage("Loading and rendering document...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                String originalDisplayName = file.getName();
                if (originalDisplayName.startsWith("local_view_") || originalDisplayName.startsWith("open_doc_")) {
                    int pos = originalDisplayName.indexOf('_', 11);
                    if (pos != -1 && pos < originalDisplayName.length() - 1) {
                        originalDisplayName = originalDisplayName.substring(pos + 1);
                    }
                }

                int dotIdx = originalDisplayName.lastIndexOf('.');
                if (dotIdx != -1) {
                    sourceExtension = originalDisplayName.substring(dotIdx).toLowerCase(Locale.ROOT);
                }

                File workingFile = file;
                try {
                    String safeName = originalDisplayName.replaceAll("[\\\\/:*?\"<>|]", "_");
                    File cacheCopy = new File(getCacheDir(), safeName);
                    if (!cacheCopy.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                        try (FileInputStream fis = new FileInputStream(file);
                             FileOutputStream fos = new FileOutputStream(cacheCopy)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = fis.read(buf)) > 0) {
                                fos.write(buf, 0, len);
                            }
                        }
                        if (cacheCopy.exists() && cacheCopy.length() > 0) {
                            workingFile = cacheCopy;
                        }
                    }
                } catch (Exception ignored) {}

                File processed = workingFile;
                String name = originalDisplayName.toLowerCase();
                if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") || name.endsWith(".dot") || name.endsWith(".docm") || name.endsWith(".dotx") || name.endsWith(".dotm")) {
                    sourceWordFile = workingFile;
                    processed = PdfProcessor.wordToPdf(
                            this,
                            workingFile,
                            originalDisplayName.replaceFirst("\\.[^.]+$", "") + "_view.pdf"
                    );
                } else if (name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp") || name.endsWith(".ppsx")) {
                    throw new IllegalArgumentException("PowerPoint files are currently not supported by Asterine.");
                } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")) {
                    processed = PdfProcessor.textToPdf(this, workingFile, originalDisplayName.replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                }

                final String finalDisplayName = originalDisplayName;
                File finalProcessed = processed;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    pdfFile = finalProcessed;
                    if (isFromUri && pdfFile != null && pdfFile.exists()) {
                        String lower = finalDisplayName.toLowerCase();
                        String docType = "PDF";
                        if (lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".rtf") || lower.endsWith(".odt") || lower.endsWith(".dot") || lower.endsWith(".docm") || lower.endsWith(".dotx") || lower.endsWith(".dotm")) {
                            docType = "WORD";
                        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
                            docType = "EXCEL";
                        } else if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md") || lower.endsWith(".xml") || lower.endsWith(".json")) {
                            docType = "TXT";
                        }

                        DocumentItem item = new DocumentItem(
                                String.valueOf(System.currentTimeMillis()),
                                finalDisplayName,
                                pdfFile.getAbsolutePath(),
                                docType,
                                pdfFile.length(),
                                System.currentTimeMillis(),
                                "Recent"
                        );
                        DocumentRepository.getInstance(this).addDocument(item);
                    }

                    if (pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0) {
                        Toast.makeText(this, "File not found or unreadable.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    initViews();
                    setupToolbar();
                    setupReaderView();
                    setupEditControls();
                    setFullscreenMode(false);
                    showFloatingControlsAndResetTimer();
                });
            } catch (Exception e) {
                Log.e("PdfViewerActivity", "Document processing failed", e);

                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    Toast.makeText(
                            this,
                            "Could not process this document. The original file has not been changed.",
                            Toast.LENGTH_LONG
                    ).show();

                    // Keep the reader open rather than closing the activity.
                    // Do not load an empty or corrupted PDF.
                });
            }
        }).start();
    }

    private void initViews() {
        layoutTopBarContainer = findViewById(R.id.layoutTopBarContainer);
        btnTopBack = findViewById(R.id.btnTopBack);
        btnTopRotate = findViewById(R.id.btnTopRotate);
        btnTopInfo = findViewById(R.id.btnTopInfo);
        btnTopShare = findViewById(R.id.btnTopShare);
        btnTopMenu = findViewById(R.id.btnTopMenu);

        layoutVerticalScrollPill = findViewById(R.id.layoutVerticalScrollPill);
        layoutPageCounterPill = findViewById(R.id.layoutPageCounterPill);
        tvPageBadgeCounter = findViewById(R.id.tvPageBadgeCounter);

        fabEditDocument = findViewById(R.id.fabEditDocument);

        cardEditControls = findViewById(R.id.cardEditControls);
        btnToolPen = findViewById(R.id.btnToolPen);
        btnToolHighlighter = findViewById(R.id.btnToolHighlighter);
        btnToolEraser = findViewById(R.id.btnToolEraser);
        btnColorRed = findViewById(R.id.btnColorRed);
        btnColorBlue = findViewById(R.id.btnColorBlue);
        btnColorBlack = findViewById(R.id.btnColorBlack);
        btnColorGreen = findViewById(R.id.btnColorGreen);
        btnColorYellow = findViewById(R.id.btnColorYellow);
        btnUndoDraw = findViewById(R.id.btnUndoDraw);
        btnRedoDraw = findViewById(R.id.btnRedoDraw);
        btnClearDraw = findViewById(R.id.btnClearDraw);
        btnCancelEditMode = findViewById(R.id.btnCancelEditMode);
        btnDoneEditMode = findViewById(R.id.btnDoneEditMode);

        fabToggleTools = findViewById(R.id.fabToggleTools);
        tvZoomToast = findViewById(R.id.tvZoomToast);

        rvPdfPages = findViewById(R.id.rvPdfPages);

        ViewCompat.setOnApplyWindowInsetsListener(layoutTopBarContainer, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(cardEditControls, (v, insets) -> {
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBars.bottom + dpToPx(12));
            return insets;
        });
    }

    private void showZoomToast(float zoomScale) {
        if (tvZoomToast == null) return;
        int pct = Math.round(zoomScale * 100);
        tvZoomToast.setText(pct + "%");
        tvZoomToast.setVisibility(View.VISIBLE);

        if (zoomToastRunnable != null) {
            handler.removeCallbacks(zoomToastRunnable);
        }
        zoomToastRunnable = () -> {
            if (tvZoomToast != null) {
                tvZoomToast.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    tvZoomToast.setVisibility(View.GONE);
                    tvZoomToast.setAlpha(1.0f);
                }).start();
            }
        };
        handler.postDelayed(zoomToastRunnable, 1200);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupToolbar() {
        if (btnTopBack != null) btnTopBack.setOnClickListener(v -> finish());
        if (btnTopRotate != null) btnTopRotate.setOnClickListener(v -> toggleScreenOrientation());
        if (btnTopInfo != null) btnTopInfo.setOnClickListener(v -> showDocumentDetails());
        if (btnTopShare != null) btnTopShare.setOnClickListener(v -> shareFile());
        if (btnTopMenu != null) btnTopMenu.setOnClickListener(this::showPopupMenu);
        if (fabEditDocument != null) fabEditDocument.setOnClickListener(v -> enterEditMode());
    }

    private boolean isWordDocument() {
        if (sourceWordFile != null && sourceWordFile.exists()) return true;
        if (pdfFile != null && pdfFile.getName().toLowerCase(Locale.ROOT).endsWith("_view.pdf")) return true;
        String ext = sourceExtension != null ? sourceExtension.toLowerCase(Locale.ROOT) : "";
        return ext.contains("doc") || ext.contains("odt") || ext.contains("rtf") || ext.contains("dot") || ext.contains("xml") || ext.contains("html");
    }

    private void showPopupMenu(View view) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, view);
        popup.getMenu().add(0, 101, 0, "Go to Page");
        popup.getMenu().add(0, 102, 1, "Page Overview");

        if (isWordDocument()) {
            popup.getMenu().add(0, 103, 2, "Convert to PDF");
        }

        popup.getMenu().add(0, 104, 3, "Sign PDF");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 101) {
                showGotoPageDialog();
                return true;
            } else if (id == 102) {
                showThumbnailGrid();
                return true;
            } else if (id == 103) {
                saveWordAsPdf();
                return true;
            } else if (id == 104) {
                openToolForCurrentDoc("SIGN", "Sign PDF");
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void saveWordAsPdf() {
        if (pdfFile != null && pdfFile.exists()) {
            try {
                File targetDir = AsterineStorageManager.getAsterineDirectory(this);
                String displayName = (sourceWordFile != null ? sourceWordFile.getName() : pdfFile.getName()).replaceFirst("\\.[^.]+$", "") + "_Converted.pdf";
                File outputPdf = AsterineStorageManager.getUniqueFile(targetDir, displayName);

                try (FileInputStream fis = new FileInputStream(pdfFile);
                     FileOutputStream fos = new FileOutputStream(outputPdf)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }

                DocumentItem newItem = new DocumentItem(
                        String.valueOf(System.currentTimeMillis()),
                        outputPdf.getName(),
                        outputPdf.getAbsolutePath(),
                        "PDF",
                        outputPdf.length(),
                        System.currentTimeMillis(),
                        "Created"
                );
                DocumentRepository.getInstance(this).addDocument(newItem);

                BrandedToast.show(this, "Converted and saved as " + outputPdf.getName() + " in Documents/Asterine");
            } catch (Exception e) {
                BrandedToast.show(this, "Conversion failed: " + e.getMessage());
            }
        }
    }

    private void scrollToPage(int pageIndex) {
        int totalPages = pageAdapter.getItemCount();
        if (pageIndex < 0 || pageIndex >= totalPages) return;
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(pageIndex, 0);
            updatePageIndicator();
        }
    }

    private void setupReaderView() {
        layoutManager = new LinearLayoutManager(this);
        rvPdfPages.setLayoutManager(layoutManager);

        pageAdapter = new PdfPageAdapter(pdfFile, this);
        rvPdfPages.setAdapter(pageAdapter);

        updatePageIndicator();
        rvPdfPages.post(this::updateVerticalScrollPillPosition);

        rvPdfPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updatePageIndicator();
                updateVerticalScrollPillPosition();
                showFloatingControlsAndResetTimer();
            }
        });

        if (layoutVerticalScrollPill != null) {
            layoutVerticalScrollPill.setOnTouchListener(new View.OnTouchListener() {
                private float startRawY;
                private float startTranslationY;
                private boolean isDragging = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    showFloatingControlsAndResetTimer();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startRawY = event.getRawY();
                            startTranslationY = v.getTranslationY();
                            isDragging = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaY = event.getRawY() - startRawY;
                            if (Math.abs(deltaY) > 6) {
                                isDragging = true;
                            }
                            if (isDragging && rvPdfPages != null && pageAdapter != null) {
                                int parentHeight = rvPdfPages.getHeight();
                                int pillHeight = v.getHeight();
                                float maxTravel = parentHeight - pillHeight - dpToPx(140);
                                if (maxTravel > 0) {
                                    float newTranslationY = Math.max(0f, Math.min(maxTravel, startTranslationY + deltaY));
                                    v.setTranslationY(newTranslationY);

                                    float fraction = newTranslationY / maxTravel;
                                    int totalPages = pageAdapter.getItemCount();
                                    if (totalPages > 0) {
                                        int targetPage = Math.min(totalPages - 1, Math.max(0, Math.round(fraction * (totalPages - 1))));
                                        if (layoutManager != null) {
                                            layoutManager.scrollToPositionWithOffset(targetPage, 0);
                                        }
                                        updatePageIndicator();
                                    }
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!isDragging) {
                                v.performClick();
                                float touchY = event.getY();
                                if (touchY < v.getHeight() / 2f) {
                                    int currentPos = layoutManager.findFirstVisibleItemPosition();
                                    if (currentPos > 0) scrollToPage(currentPos - 1);
                                } else {
                                    int currentPos = layoutManager.findFirstVisibleItemPosition();
                                    if (currentPos < pageAdapter.getItemCount() - 1) scrollToPage(currentPos + 1);
                                }
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        if (layoutPageCounterPill != null) {
            layoutPageCounterPill.setOnClickListener(v -> showGotoPageDialog());
        }
        if (tvPageBadgeCounter != null) {
            tvPageBadgeCounter.setOnClickListener(v -> showGotoPageDialog());
        }

        if (fabEditDocument != null) {
            fabEditDocument.setOnClickListener(v -> enterEditMode());
        }

        if (fabToggleTools != null) {
            fabToggleTools.setOnClickListener(v -> onPageClick());
        }
    }

    private void updateVerticalScrollPillPosition() {
        if (layoutVerticalScrollPill == null || rvPdfPages == null) return;
        int scrollRange = rvPdfPages.computeVerticalScrollRange() - rvPdfPages.computeVerticalScrollExtent();
        if (scrollRange > 0) {
            int scrollOffset = rvPdfPages.computeVerticalScrollOffset();
            float scrollFraction = Math.max(0f, Math.min(1f, (float) scrollOffset / scrollRange));

            int parentHeight = rvPdfPages.getHeight();
            int pillHeight = layoutVerticalScrollPill.getHeight();
            if (parentHeight > 0 && pillHeight > 0) {
                float maxTravel = parentHeight - pillHeight - dpToPx(140);
                if (maxTravel > 0) {
                    layoutVerticalScrollPill.setTranslationY(scrollFraction * maxTravel);
                }
            }
        }
    }

    private void toggleScreenOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            BrandedToast.show(this, "Landscape Mode");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            BrandedToast.show(this, "Portrait Mode");
        }
    }

    private void setupEditControls() {
        btnToolPen.setOnClickListener(v -> {
            selectedTool = DrawingCanvasView.ToolMode.PEN;
            updateDrawingTool();
        });

        btnToolHighlighter.setOnClickListener(v -> {
            selectedTool = DrawingCanvasView.ToolMode.HIGHLIGHTER;
            updateDrawingTool();
        });

        btnToolEraser.setOnClickListener(v -> {
            selectedTool = DrawingCanvasView.ToolMode.ERASER;
            updateDrawingTool();
        });

        btnColorRed.setOnClickListener(v -> {
            selectedColor = Color.RED;
            updateDrawingTool();
        });

        btnColorBlue.setOnClickListener(v -> {
            selectedColor = Color.parseColor("#1E88E5");
            updateDrawingTool();
        });

        btnColorBlack.setOnClickListener(v -> {
            selectedColor = Color.BLACK;
            updateDrawingTool();
        });

        btnColorGreen.setOnClickListener(v -> {
            selectedColor = Color.parseColor("#43A047");
            updateDrawingTool();
        });

        btnColorYellow.setOnClickListener(v -> {
            selectedColor = Color.parseColor("#FFEB3B");
            updateDrawingTool();
        });

        btnUndoDraw.setOnClickListener(v -> {
            int currentPos = layoutManager.findFirstVisibleItemPosition();
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(currentPos);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).drawingCanvas.undo();
            }
        });

        btnRedoDraw.setOnClickListener(v -> {
            int currentPos = layoutManager.findFirstVisibleItemPosition();
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(currentPos);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).drawingCanvas.redo();
            }
        });

        btnClearDraw.setOnClickListener(v -> {
            int currentPos = layoutManager.findFirstVisibleItemPosition();
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(currentPos);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).drawingCanvas.clear();
            }
        });

        btnCancelEditMode.setOnClickListener(v -> exitEditModeWithConfirmation());

        btnDoneEditMode.setOnClickListener(v -> {
            Toast.makeText(this, "Markup saved to document!", Toast.LENGTH_SHORT).show();
            exitEditMode();
        });
    }

    private void exitEditModeWithConfirmation() {
        if (hasAnyUnsavedDrawings()) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Annotations")
                    .setMessage("You have unsaved annotations on the document. Would you like to save them?")
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        Toast.makeText(this, "Markup saved to document!", Toast.LENGTH_SHORT).show();
                        exitEditMode();
                    })
                    .setNegativeButton("Discard", (dialog, which) -> {
                        clearAllDrawings();
                        exitEditMode();
                    })
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        } else {
            exitEditMode();
        }
    }

    private boolean hasAnyUnsavedDrawings() {
        if (rvPdfPages == null || pageAdapter == null) return false;
        int count = pageAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(i);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                if (((PdfPageAdapter.PageViewHolder) holder).drawingCanvas.hasDrawings()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clearAllDrawings() {
        if (rvPdfPages == null || pageAdapter == null) return;
        int count = pageAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(i);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).drawingCanvas.clear();
            }
        }
    }

    private void enterEditMode() {
        isEditMode = true;
        if (layoutVerticalScrollPill != null) layoutVerticalScrollPill.setVisibility(View.GONE);
        if (layoutPageCounterPill != null) layoutPageCounterPill.setVisibility(View.GONE);
        if (fabEditDocument != null) fabEditDocument.setVisibility(View.GONE);
        if (cardEditControls != null) cardEditControls.setVisibility(View.VISIBLE);
        selectedTool = DrawingCanvasView.ToolMode.PEN;
        updateDrawingTool();
        Toast.makeText(this, "Draw & Edit Mode Activated. Draw on any page!", Toast.LENGTH_SHORT).show();
    }

    private void exitEditMode() {
        isEditMode = false;
        if (cardEditControls != null) cardEditControls.setVisibility(View.GONE);
        showFloatingControlsAndResetTimer();
        pageAdapter.setDrawingTool(DrawingCanvasView.ToolMode.NONE, selectedColor, 8f);
    }

    private void updateDrawingTool() {
        float strokeWidth = (selectedTool == DrawingCanvasView.ToolMode.HIGHLIGHTER) ? 20f : 8f;
        pageAdapter.setDrawingTool(selectedTool, selectedColor, strokeWidth);
    }

    private void showGotoPageDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_goto_page);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvPageRangePrompt = dialog.findViewById(R.id.tvPageRangePrompt);
        EditText etPageNumberInput = dialog.findViewById(R.id.etPageNumberInput);
        View btnCancelGoto = dialog.findViewById(R.id.btnCancelGoto);
        View btnConfirmGoto = dialog.findViewById(R.id.btnConfirmGoto);

        int totalPages = pageAdapter.getItemCount();
        int currentPos = layoutManager.findFirstVisibleItemPosition() + 1;

        tvPageRangePrompt.setText("Enter page number (1 - " + totalPages + ")");
        etPageNumberInput.setText(String.valueOf(currentPos));
        etPageNumberInput.selectAll();

        btnCancelGoto.setOnClickListener(v -> dialog.dismiss());

        btnConfirmGoto.setOnClickListener(v -> {
            String input = etPageNumberInput.getText().toString().trim();
            try {
                int pageNum = Integer.parseInt(input);
                if (pageNum >= 1 && pageNum <= totalPages) {
                    scrollToPage(pageNum - 1);
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Page number must be between 1 and " + totalPages, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showThumbnailGrid() {
        if (pageAdapter == null || pageAdapter.getRenderer() == null) return;

        BottomSheetDialog gridDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_thumbnail_grid, null);
        gridDialog.setContentView(sheetView);

        TextView tvGridTitle = sheetView.findViewById(R.id.tvGridTitle);
        ImageButton btnCloseGrid = sheetView.findViewById(R.id.btnCloseGrid);
        RecyclerView rvThumbnails = sheetView.findViewById(R.id.rvThumbnails);

        tvGridTitle.setText(getString(R.string.page_overview_title, pageAdapter.getItemCount()));
        btnCloseGrid.setOnClickListener(v -> gridDialog.dismiss());

        rvThumbnails.setLayoutManager(new GridLayoutManager(this, 3));
        int currentPos = layoutManager.findFirstVisibleItemPosition();

        ThumbnailGridAdapter gridAdapter = new ThumbnailGridAdapter(
                pageAdapter.getRenderer(),
                currentPos,
                position -> {
                    scrollToPage(position);
                    gridDialog.dismiss();
                }
        );

        rvThumbnails.setAdapter(gridAdapter);
        gridDialog.setOnDismissListener(dialog -> gridAdapter.cleanup());

        gridDialog.show();
    }

    private void showDocumentDetails() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_doc_info);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvInfoFileName = dialog.findViewById(R.id.tvInfoFileName);
        TextView tvInfoFileSizePages = dialog.findViewById(R.id.tvInfoFileSizePages);
        TextView tvInfoFilePath = dialog.findViewById(R.id.tvInfoFilePath);
        View btnCloseDocInfo = dialog.findViewById(R.id.btnCloseDocInfo);

        File targetFile = sourceWordFile != null && sourceWordFile.exists() ? sourceWordFile : pdfFile;
        String originalName = targetFile != null ? targetFile.getName() : "Document";
        if (originalName.startsWith("local_view_") || originalName.startsWith("open_doc_")) {
            int pos = originalName.indexOf('_', 11);
            if (pos != -1 && pos < originalName.length() - 1) {
                originalName = originalName.substring(pos + 1);
            }
        }
        originalName = originalName.replaceFirst("_view\\.pdf$", "");

        String docType = "PDF Document";
        if (sourceExtension.equals(".docx") || sourceExtension.equals(".doc") || sourceExtension.equals(".rtf") || sourceExtension.equals(".odt") || sourceExtension.equals(".dot") || sourceExtension.equals(".docm") || sourceExtension.equals(".dotx") || sourceExtension.equals(".dotm")) {
            docType = "Word Document";
        } else if (sourceExtension.equals(".xlsx") || sourceExtension.equals(".xls") || sourceExtension.equals(".csv")) {
            docType = "Excel Spreadsheet";
        } else if (sourceExtension.equals(".txt") || sourceExtension.equals(".log") || sourceExtension.equals(".json") || sourceExtension.equals(".xml") || sourceExtension.equals(".md")) {
            docType = "Text Document";
        }

        tvInfoFileName.setText(originalName);
        long fileSize = targetFile != null ? targetFile.length() : (pdfFile != null ? pdfFile.length() : 0);
        int pageCount = pageAdapter != null ? pageAdapter.getItemCount() : 1;
        tvInfoFileSizePages.setText(docType + " • " + formatFileSize(fileSize) + " • " + pageCount + " Pages");
        tvInfoFilePath.setText(targetFile != null ? targetFile.getAbsolutePath() : (pdfFile != null ? pdfFile.getAbsolutePath() : ""));

        btnCloseDocInfo.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void setFullscreenMode(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enable) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            if (enable) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    @Override
    public void onPageClick() {
        if (isEditMode) return;
        isUiVisible = !isUiVisible;
        int topBarVis = isUiVisible ? View.VISIBLE : View.GONE;
        if (layoutTopBarContainer != null) {
            layoutTopBarContainer.setVisibility(topBarVis);
        }
        setFullscreenMode(!isUiVisible);
        showFloatingControlsAndResetTimer();
    }

    private File getFileFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            String fileName = "document_" + System.currentTimeMillis();
            String extension = ".pdf";

            if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String name = cursor.getString(nameIndex);
                            if (name != null) {
                                fileName = name;
                                int dot = name.lastIndexOf('.');
                                if (dot != -1) {
                                    extension = name.substring(dot);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else if (uri.getPath() != null) {
                File pathFile = new File(uri.getPath());
                fileName = pathFile.getName();
                int dot = fileName.lastIndexOf('.');
                if (dot != -1) {
                    extension = fileName.substring(dot);
                }
            }

            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File tempFile = new File(getCacheDir(), safeName);
            if (tempFile.exists()) {
                tempFile.delete();
            }
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

    private void openToolForCurrentDoc(String toolType, String toolTitle) {
        if (pdfFile != null && pdfFile.exists()) {
            Intent intent;
            if ("SIGN".equals(toolType)) {
                intent = new Intent(this, SignPdfActivity.class);
            } else {
                intent = new Intent(this, ToolActionActivity.class);
            }
            intent.putExtra("TOOL_TYPE", toolType);
            intent.putExtra("TOOL_TITLE", toolTitle);
            intent.putExtra("PRELOAD_FILE_PATH", pdfFile.getAbsolutePath());
            startActivity(intent);
        }
    }

    private void updatePageIndicator() {
        if (layoutManager == null || pageAdapter == null) return;
        int currentPos = layoutManager.findFirstVisibleItemPosition() + 1;
        int totalPages = pageAdapter.getItemCount();
        if (currentPos <= 0) currentPos = 1;

        String formattedBadge = currentPos + "/" + totalPages;

        if (tvPageBadgeCounter != null) {
            tvPageBadgeCounter.setText(formattedBadge);
        }
    }

    private void shareFile() {
        if (pdfFile != null && pdfFile.exists()) {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share Document Reader"));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to share file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        floatingControlsHandler.removeCallbacksAndMessages(null);
        handler.removeCallbacksAndMessages(null);
        if (pageAdapter != null) {
            pageAdapter.closeRenderer();
        }
    }
}
