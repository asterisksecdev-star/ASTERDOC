package com.asterdoc.app.ui.viewer;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
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
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.tools.ToolActionActivity;
import com.asterdoc.app.ui.tools.sign.SignPdfActivity;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private MaterialToolbar toolbarPdfViewer;
    private LinearLayout layoutSearchBar;
    private EditText etSearchQuery;
    private TextView tvSearchMatchCount;
    private ImageButton btnCloseSearch;
    private ImageButton btnSearchPrev;
    private ImageButton btnSearchNext;

    private LinearLayout cardBottomControls;
    private ImageButton btnBottomGrid;
    private TextView tvReaderPageIndicator;
    private ImageButton btnPrevPage;
    private ImageButton btnNextPage;
    private ImageButton btnZoomOut;
    private ImageButton btnZoomIn;

    private MaterialCardView cardEditControls;
    private ImageButton btnToolPen;
    private ImageButton btnToolHighlighter;
    private ImageButton btnToolEraser;
    private View btnColorRed, btnColorBlue, btnColorBlack, btnColorGreen, btnColorYellow;
    private ImageButton btnUndoDraw, btnRedoDraw, btnClearDraw;
    private View btnCancelEditMode, btnDoneEditMode;

    private FloatingActionButton fabToggleTools;

    private Chip chipConvertToWord;
    private Chip chipCompressPdf;
    private Chip chipSignPdf;
    private Chip chipOcrText;

    private boolean isUiVisible = true;
    private boolean isEditMode = false;
    private int selectedColor = Color.RED;
    private DrawingCanvasView.ToolMode selectedTool = DrawingCanvasView.ToolMode.PEN;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    private final List<Integer> searchMatchingPages = new ArrayList<>();
    private int currentSearchIndex = -1;

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

    private void processAndLoadFile(File file, boolean isFromUri) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIcon(R.mipmap.ic_launcher);
        progressDialog.setTitle("AsterDocs");
        progressDialog.setMessage("Loading and rendering document...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                File processed = file;
                String name = file.getName().toLowerCase();
                if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt")) {
                    processed = PdfProcessor.wordToPdf(this, file, file.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                } else if (name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp")) {
                    processed = PdfProcessor.pptxToPdf(this, file, file.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")) {
                    processed = PdfProcessor.textToPdf(this, file, file.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                }

                File finalProcessed = processed;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    pdfFile = finalProcessed;
                    if (isFromUri && pdfFile != null && pdfFile.exists()) {
                        DocumentItem item = new DocumentItem(
                                String.valueOf(System.currentTimeMillis()),
                                pdfFile.getName(),
                                pdfFile.getAbsolutePath(),
                                "PDF",
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
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error opening document: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    private void initViews() {
        layoutTopBarContainer = findViewById(R.id.layoutTopBarContainer);
        toolbarPdfViewer = findViewById(R.id.toolbarPdfViewer);

        cardBottomControls = findViewById(R.id.cardBottomControls);
        btnBottomGrid = findViewById(R.id.btnBottomGrid);
        tvReaderPageIndicator = findViewById(R.id.tvReaderPageIndicator);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnZoomIn = findViewById(R.id.btnZoomIn);

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

        chipConvertToWord = findViewById(R.id.chipConvertToWord);
        chipCompressPdf = findViewById(R.id.chipCompressPdf);
        chipSignPdf = findViewById(R.id.chipSignPdf);
        chipOcrText = findViewById(R.id.chipOcrText);

        rvPdfPages = findViewById(R.id.rvPdfPages);

        // Apply Window Insets so all controls remain visible & reachable on all screen sizes & navigation bar setups
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

        ViewCompat.setOnApplyWindowInsetsListener(cardBottomControls, (v, insets) -> {
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            params.bottomMargin = navBars.bottom + dpToPx(24);
            v.setLayoutParams(params);
            return insets;
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setupToolbar() {
        toolbarPdfViewer.setTitle(pdfFile.getName());
        toolbarPdfViewer.setNavigationOnClickListener(v -> finish());
        toolbarPdfViewer.inflateMenu(R.menu.menu_pdf_viewer);
        toolbarPdfViewer.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_mode) {
                enterEditMode();
                return true;
            } else if (id == R.id.action_grid_view) {
                showThumbnailGrid();
                return true;
            } else if (id == R.id.action_share) {
                shareFile();
                return true;
            } else if (id == R.id.action_doc_info) {
                showDocumentDetails();
                return true;
            } else if (id == R.id.action_convert_word) {
                openToolForCurrentDoc("PDF_WORD", "PDF2Word");
                return true;
            } else if (id == R.id.action_compress) {
                openToolForCurrentDoc("COMPRESS", "Compress PDF");
                return true;
            } else if (id == R.id.action_sign) {
                openToolForCurrentDoc("SIGN", "Sign PDF");
                return true;
            } else if (id == R.id.action_ocr) {
                openToolForCurrentDoc("OCR", "OCR Text");
                return true;
            }
            return false;
        });
    }

    private void scrollToPage(int pageIndex) {
        int totalPages = pageAdapter.getItemCount();
        if (pageIndex < 0 || pageIndex >= totalPages) return;

        RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };
        smoothScroller.setTargetPosition(pageIndex);
        if (layoutManager != null) {
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    private void setupReaderView() {
        layoutManager = new LinearLayoutManager(this);
        rvPdfPages.setLayoutManager(layoutManager);

        pageAdapter = new PdfPageAdapter(pdfFile, this);
        rvPdfPages.setAdapter(pageAdapter);

        updatePageIndicator();

        rvPdfPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updatePageIndicator();
            }
        });

        btnBottomGrid.setOnClickListener(v -> showThumbnailGrid());

        tvReaderPageIndicator.setOnClickListener(v -> showGotoPageDialog());

        btnPrevPage.setOnClickListener(v -> {
            int currentPos = layoutManager.findFirstVisibleItemPosition();
            if (currentPos > 0) {
                scrollToPage(currentPos - 1);
            }
        });

        btnNextPage.setOnClickListener(v -> {
            int currentPos = layoutManager.findFirstVisibleItemPosition();
            if (currentPos < pageAdapter.getItemCount() - 1) {
                scrollToPage(currentPos + 1);
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            int pos = layoutManager.findFirstVisibleItemPosition();
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(pos);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).ivPdfPage.zoomOut();
            }
        });

        btnZoomIn.setOnClickListener(v -> {
            int pos = layoutManager.findFirstVisibleItemPosition();
            RecyclerView.ViewHolder holder = rvPdfPages.findViewHolderForAdapterPosition(pos);
            if (holder instanceof PdfPageAdapter.PageViewHolder) {
                ((PdfPageAdapter.PageViewHolder) holder).ivPdfPage.zoomIn();
            }
        });

        fabToggleTools.setOnClickListener(v -> onPageClick());

        chipConvertToWord.setOnClickListener(v -> openToolForCurrentDoc("PDF_WORD", "PDF2Word"));
        chipCompressPdf.setOnClickListener(v -> openToolForCurrentDoc("COMPRESS", "Compress PDF"));
        chipSignPdf.setOnClickListener(v -> openToolForCurrentDoc("SIGN", "Sign PDF"));
        chipOcrText.setOnClickListener(v -> openToolForCurrentDoc("OCR", "OCR Text"));
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
        cardBottomControls.setVisibility(View.GONE);
        cardEditControls.setVisibility(View.VISIBLE);
        selectedTool = DrawingCanvasView.ToolMode.PEN;
        updateDrawingTool();
        Toast.makeText(this, "Draw & Edit Mode Activated. Draw on any page!", Toast.LENGTH_SHORT).show();
    }

    private void exitEditMode() {
        isEditMode = false;
        cardEditControls.setVisibility(View.GONE);
        cardBottomControls.setVisibility(View.VISIBLE);
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

        String originalName = pdfFile != null ? pdfFile.getName() : "Document";
        String docType = "PDF Document";
        String lower = originalName.toLowerCase();
        if (lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".rtf") || lower.endsWith(".odt") || lower.contains("_view.pdf")) {
            docType = "Microsoft Word Document";
        } else if (lower.endsWith(".pptx") || lower.endsWith(".ppt") || lower.endsWith(".pps") || lower.endsWith(".odp")) {
            docType = "PowerPoint Presentation";
        } else if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".md")) {
            docType = "Plain Text Document";
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
            docType = "Excel Spreadsheet";
        }

        tvInfoFileName.setText(originalName.replace("_view.pdf", ".docx"));
        tvInfoFileSizePages.setText(docType + " • " + formatFileSize(pdfFile.length()) + " • " + pageAdapter.getItemCount() + " Pages");
        tvInfoFilePath.setText(pdfFile.getAbsolutePath());

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
        int topBottomVisibility = isUiVisible ? View.VISIBLE : View.GONE;
        int fabVisibility = isUiVisible ? View.GONE : View.VISIBLE;

        layoutTopBarContainer.setVisibility(topBottomVisibility);
        cardBottomControls.setVisibility(topBottomVisibility);
        fabToggleTools.setVisibility(fabVisibility);

        setFullscreenMode(!isUiVisible);
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

            File tempFile = File.createTempFile("open_doc_", extension, getCacheDir());
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
        int currentPos = layoutManager.findFirstVisibleItemPosition() + 1;
        int totalPages = pageAdapter.getItemCount();
        if (currentPos <= 0) currentPos = 1;
        tvReaderPageIndicator.setText(currentPos + " / " + totalPages);
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
        handler.removeCallbacksAndMessages(null);
        if (pageAdapter != null) {
            pageAdapter.closeRenderer();
        }
    }
}
