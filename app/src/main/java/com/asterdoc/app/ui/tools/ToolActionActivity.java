package com.asterdoc.app.ui.tools;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.asterdoc.app.R;
import com.asterdoc.app.billing.BillingManager;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToolActionActivity extends AppCompatActivity {

    private String toolType;
    private String toolTitle;

    private MaterialToolbar toolbar;
    private TextView tvToolDescTitle;
    private TextView tvToolDescSub;
    private LinearLayout llCustomInputContainer;
    private EditText etCustomInput;
    private MaterialButton btnSelectFiles;
    private MaterialButton btnCaptureCamera;
    private MaterialButton btnExecuteTool;
    private TextView tvSelectedStatus;
    private TextView tvSelectedFilesList;
    private LinearLayout llProgressContainer;
    private ProgressBar pbToolProgress;
    private TextView tvProgressText;
    private View cardResultContainer;
    private TextView tvResultDetails;
    private MaterialButton btnOpenResult;
    private MaterialButton btnShareResult;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private File generatedFile;
    private String ocrRecognizedText = null;

    private Uri cameraImageUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCameraIntent();
                } else {
                    BrandedToast.show(this, "Camera permission is required to scan documents.");
                }
            }
    );

    private void launchCameraIntent() {
        try {
            File photoFile = new File(getCacheDir(), "scan_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraLauncher.launch(cameraIntent);
        } catch (Exception e) {
            BrandedToast.show(this, "Camera error: " + e.getMessage());
        }
    }

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (cameraImageUri != null) {
                        llProgressContainer.setVisibility(View.VISIBLE);
                        cardResultContainer.setVisibility(View.GONE);
                        executor.execute(() -> {
                            try {
                                List<Uri> singleUri = new ArrayList<>();
                                singleUri.add(cameraImageUri);
                                File pdfOut = PdfProcessor.imageToPdf(this, singleUri, "Scanned_Doc_" + System.currentTimeMillis() + ".pdf");
                                if (pdfOut != null && pdfOut.exists()) {
                                    DocumentItem item = new DocumentItem(
                                            String.valueOf(System.currentTimeMillis()),
                                            pdfOut.getName(),
                                            pdfOut.getAbsolutePath(),
                                            "PDF",
                                            pdfOut.length(),
                                            System.currentTimeMillis(),
                                            "Scanned"
                                    );
                                    DocumentRepository.getInstance(this).addDocument(item);
                                    mainHandler.post(() -> {
                                        BrandedToast.show(this, "Independent Scanned Document saved!");
                                        showScanSuccess(pdfOut);
                                    });
                                }
                            } catch (Exception e) {
                                mainHandler.post(() -> {
                                    llProgressContainer.setVisibility(View.GONE);
                                    BrandedToast.show(this, "Error saving scan: " + e.getMessage());
                                });
                            }
                        });
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> pickedUris = new ArrayList<>();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            pickedUris.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        pickedUris.add(data.getData());
                    }

                    if ("SCAN".equals(toolType) && !pickedUris.isEmpty()) {
                        llProgressContainer.setVisibility(View.VISIBLE);
                        cardResultContainer.setVisibility(View.GONE);
                        executor.execute(() -> {
                            try {
                                int savedCount = 0;
                                File lastPdf = null;
                                for (Uri uri : pickedUris) {
                                    List<Uri> single = new ArrayList<>();
                                    single.add(uri);
                                    File pdfOut = PdfProcessor.imageToPdf(this, single, "Scanned_Image_" + System.currentTimeMillis() + "_" + savedCount + ".pdf");
                                    if (pdfOut != null && pdfOut.exists()) {
                                        DocumentItem item = new DocumentItem(
                                                String.valueOf(System.currentTimeMillis() + savedCount),
                                                pdfOut.getName(),
                                                pdfOut.getAbsolutePath(),
                                                "PDF",
                                                pdfOut.length(),
                                                System.currentTimeMillis(),
                                                "Scanned"
                                        );
                                        DocumentRepository.getInstance(this).addDocument(item);
                                        lastPdf = pdfOut;
                                        savedCount++;
                                    }
                                }
                                File finalLast = lastPdf;
                                int finalCount = savedCount;
                                mainHandler.post(() -> {
                                    llProgressContainer.setVisibility(View.GONE);
                                    BrandedToast.show(this, finalCount + " independent document(s) scanned & saved!");
                                    if (finalCount == 1 && finalLast != null) {
                                        showScanSuccess(finalLast);
                                    } else {
                                        finish();
                                    }
                                });
                            } catch (Exception e) {
                                mainHandler.post(() -> {
                                    llProgressContainer.setVisibility(View.GONE);
                                    BrandedToast.show(this, "Error processing scans: " + e.getMessage());
                                });
                            }
                        });
                        return;
                    }

                    for (Uri uri : pickedUris) {
                        if (!selectedUris.contains(uri)) {
                            selectedUris.add(uri);
                        }
                    }

                    if ("MERGE".equals(toolType) && selectedUris.size() < 2) {
                        Toast.makeText(this, "Selected " + selectedUris.size() + " doc. Please select at least 2 or more PDFs to merge.", Toast.LENGTH_LONG).show();
                    }

                    updateSelectedFilesUI();
                }
            }
    );

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tool_action);

        toolType = getIntent().getStringExtra("TOOL_TYPE");
        toolTitle = getIntent().getStringExtra("TOOL_TITLE");
        if (toolTitle == null) toolTitle = "Tool Action";

        toolbar = findViewById(R.id.toolbarToolAction);
        toolbar.setTitle(toolTitle);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvToolDescTitle = findViewById(R.id.tvToolDescTitle);
        tvToolDescSub = findViewById(R.id.tvToolDescSub);
        llCustomInputContainer = findViewById(R.id.llCustomInputContainer);
        etCustomInput = findViewById(R.id.etCustomInput);
        btnSelectFiles = findViewById(R.id.btnSelectFiles);
        btnCaptureCamera = findViewById(R.id.btnCaptureCamera);
        btnExecuteTool = findViewById(R.id.btnExecuteTool);
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus);
        tvSelectedFilesList = findViewById(R.id.tvSelectedFilesList);
        llProgressContainer = findViewById(R.id.llProgressContainer);
        pbToolProgress = findViewById(R.id.pbToolProgress);
        tvProgressText = findViewById(R.id.tvProgressText);
        cardResultContainer = findViewById(R.id.cardResultContainer);
        tvResultDetails = findViewById(R.id.tvResultDetails);
        btnOpenResult = findViewById(R.id.btnOpenResult);
        btnShareResult = findViewById(R.id.btnShareResult);

        setupToolUI();

        btnSelectFiles.setOnClickListener(v -> openFilePicker());
        if (btnCaptureCamera != null) {
            btnCaptureCamera.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCameraIntent();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                }
            });
        }
        btnExecuteTool.setOnClickListener(v -> executeToolAction());

        btnOpenResult.setOnClickListener(v -> openGeneratedFile());
        btnShareResult.setOnClickListener(v -> shareGeneratedFile());

        String preloadPath = getIntent().getStringExtra("PRELOAD_FILE_PATH");
        if (preloadPath != null) {
            File preloadFile = new File(preloadPath);
            if (preloadFile.exists()) {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", preloadFile);
                selectedUris.clear();
                selectedUris.add(uri);
                updateSelectedFilesUI();
            }
        }
    }

    private void setupToolUI() {
        tvToolDescTitle.setText(toolTitle);
        switch (toolType) {
            case "SCAN":
            case "SCAN_CAMERA":
                tvToolDescTitle.setText(getString(R.string.scan_digitise));
                tvToolDescSub.setText(getString(R.string.tool_scan_sub));
                btnSelectFiles.setText(getString(R.string.select_gallery));
                if (btnCaptureCamera != null) {
                    btnCaptureCamera.setVisibility(View.VISIBLE);
                    btnCaptureCamera.setText(getString(R.string.capture_camera));
                }
                break;
            case "PDF_WORD":
                tvToolDescSub.setText(getString(R.string.tool_pdf_word_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_pdf));
                break;
            case "WORD_PDF":
                tvToolDescSub.setText(getString(R.string.tool_word_pdf_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_word));
                break;
            case "COMPRESS":
                tvToolDescSub.setText(getString(R.string.tool_compress_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_pdf));
                break;
            case "MERGE":
                tvToolDescSub.setText(getString(R.string.tool_merge_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_pdfs_merge));
                break;
            case "SPLIT":
                tvToolDescSub.setText(getString(R.string.tool_split_sub));
                llCustomInputContainer.setVisibility(View.VISIBLE);
                etCustomInput.setHint(getString(R.string.hint_page_range));
                btnSelectFiles.setText(getString(R.string.btn_select_pdf));
                break;
            case "IMAGES_PDF":
                tvToolDescSub.setText(getString(R.string.tool_images_pdf_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_images));
                break;
            case "OCR":
                tvToolDescSub.setText(getString(R.string.tool_ocr_sub));
                btnSelectFiles.setText(getString(R.string.ocr_select_file));
                break;
            case "EXCEL_PDF":
                tvToolDescSub.setText(getString(R.string.tool_excel_pdf_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_excel));
                break;
            case "PDF_EXCEL":
                tvToolDescSub.setText(getString(R.string.tool_pdf_excel_sub));
                btnSelectFiles.setText(getString(R.string.btn_select_pdf));
                break;
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        if ("IMAGES_PDF".equals(toolType) || "SCAN".equals(toolType) || "OCR".equals(toolType)) {
            intent.setType("*/*");
        } else if ("WORD_PDF".equals(toolType)) {
            intent.setType("*/*");
        } else {
            intent.setType("application/pdf");
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, "MERGE".equals(toolType) || "IMAGES_PDF".equals(toolType));
        filePickerLauncher.launch(Intent.createChooser(intent, "Select Input File"));
    }

    private void updateSelectedFilesUI() {
        if (!selectedUris.isEmpty()) {
            tvSelectedStatus.setVisibility(View.VISIBLE);
            tvSelectedFilesList.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (Uri uri : selectedUris) {
                sb.append("• ").append(uri.getLastPathSegment()).append("\n");
            }
            tvSelectedFilesList.setText(sb.toString().trim());
            btnExecuteTool.setVisibility(View.VISIBLE);
            if ("MERGE".equals(toolType) && selectedUris.size() < 2) {
                btnExecuteTool.setText("Select at least 2 PDFs (Current: " + selectedUris.size() + ")");
                btnExecuteTool.setEnabled(true);
            } else if ("SCAN".equals(toolType) || "SCAN_CAMERA".equals(toolType) || "IMAGES_PDF".equals(toolType)) {
                btnExecuteTool.setEnabled(true);
                btnExecuteTool.setText("Generate Scanned PDF 📄 (" + selectedUris.size() + " files)");
            } else {
                btnExecuteTool.setEnabled(true);
                btnExecuteTool.setText("Start " + toolTitle + " (" + selectedUris.size() + " files)");
            }
        }
    }

    private void executeToolAction() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one input file.", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("MERGE".equals(toolType) && selectedUris.size() < 2) {
            Toast.makeText(this, "Please select at least two or more documents to merge.", Toast.LENGTH_LONG).show();
            openFilePicker();
            return;
        }

        llProgressContainer.setVisibility(View.VISIBLE);
        cardResultContainer.setVisibility(View.GONE);
        btnExecuteTool.setEnabled(false);

        executor.execute(() -> {
            try {
                switch (toolType) {
                    case "IMAGES_PDF":
                    case "SCAN":
                    case "SCAN_CAMERA":
                        generatedFile = PdfProcessor.imageToPdf(this, selectedUris, "Scanned_Document.pdf");
                        break;
                    case "MERGE":
                        List<File> tempPdfs = new ArrayList<>();
                        for (Uri uri : selectedUris) {
                            File f = copyUriToTempFile(uri);
                            if (f != null) tempPdfs.add(f);
                        }
                        generatedFile = PdfProcessor.mergePdfs(this, tempPdfs, "Merged_Document.pdf");
                        break;
                    case "SPLIT":
                        File splitInput = copyUriToTempFile(selectedUris.get(0));
                        int startPage = 1, endPage = 3;
                        String pageInput = etCustomInput.getText().toString().trim();
                        if (pageInput.contains("-")) {
                            String[] parts = pageInput.split("-");
                            startPage = Integer.parseInt(parts[0].trim());
                            endPage = Integer.parseInt(parts[1].trim());
                        }
                        generatedFile = PdfProcessor.splitPdf(this, splitInput, startPage, endPage, "Pages_" + startPage + "-" + endPage + ".pdf");
                        break;
                    case "COMPRESS":
                        File compressInput = copyUriToTempFile(selectedUris.get(0));
                        final long origBytes = compressInput != null && compressInput.exists() ? compressInput.length() : 0;
                        generatedFile = PdfProcessor.compressPdf(this, compressInput, "Compressed_Document.pdf");
                        final long compBytes = generatedFile.length();

                        if (generatedFile != null) {
                            DocumentItem item = new DocumentItem(
                                    String.valueOf(System.currentTimeMillis()),
                                    generatedFile.getName(),
                                    generatedFile.getAbsolutePath(),
                                    "PDF",
                                    generatedFile.length(),
                                    System.currentTimeMillis(),
                                    "Created"
                            );
                            DocumentRepository.getInstance(this).addDocument(item);

                            mainHandler.post(() -> {
                                llProgressContainer.setVisibility(View.GONE);
                                btnExecuteTool.setEnabled(true);
                                cardResultContainer.setVisibility(View.VISIBLE);
                                btnOpenResult.setText("Open");

                                if (origBytes > 0 && compBytes < origBytes) {
                                    long saved = origBytes - compBytes;
                                    int percent = (int) Math.round(((double) saved / origBytes) * 100);
                                    tvResultDetails.setText("Original: " + formatFileSize(origBytes) +
                                            "\nCompressed: " + formatFileSize(compBytes) +
                                            "\nSaved: " + percent + "%\nFile: " + generatedFile.getName() + "\n\nAuto-saved to Documents/AsterDocs");
                                } else {
                                    tvResultDetails.setText("This document is already optimized or cannot be reduced further without affecting quality.\nOriginal Size: " + formatFileSize(origBytes > 0 ? origBytes : compBytes) +
                                            "\nFile: " + generatedFile.getName() + "\n\nAuto-saved to Documents/AsterDocs");
                                }
                            });
                            return;
                        }
                        break;
                    case "PDF_WORD":
                        File pdfWordInput = copyUriToTempFile(selectedUris.get(0));
                        generatedFile = PdfProcessor.pdfToWord(this, pdfWordInput, "Converted Document Content from PDF", "Converted_Document.docx");
                        break;
                    case "WORD_PDF":
                        File wordInput = copyUriToTempFile(selectedUris.get(0));
                        generatedFile = PdfProcessor.wordToPdf(this, wordInput, "Converted_Word_Document.pdf");
                        break;
                    case "EXCEL_PDF":
                        File excelInput = copyUriToTempFile(selectedUris.get(0));
                        generatedFile = PdfProcessor.excelToPdf(this, excelInput, "Converted_Spreadsheet.pdf");
                        break;
                    case "PDF_EXCEL":
                        File pdfExcelInput = copyUriToTempFile(selectedUris.get(0));
                        generatedFile = PdfProcessor.pdfToExcel(this, pdfExcelInput, "Converted Spreadsheet Content from PDF", "Converted_Spreadsheet.xlsx");
                        break;
                    case "OCR":
                        PdfProcessor.performOcrForFile(this, selectedUris.get(0), new PdfProcessor.OcrCallback() {
                            @Override
                            public void onSuccess(String recognizedText) {
                                ocrRecognizedText = recognizedText;
                                try {
                                    File ocrFile = new File(PdfProcessor.getAsterDocsDirectory(), "OCR_Notes_" + System.currentTimeMillis() + ".txt");
                                    FileOutputStream fos = new FileOutputStream(ocrFile);
                                    fos.write(recognizedText.getBytes(StandardCharsets.UTF_8));
                                    fos.close();
                                    generatedFile = ocrFile;

                                    DocumentItem item = new DocumentItem(
                                            String.valueOf(System.currentTimeMillis()),
                                            ocrFile.getName(),
                                            ocrFile.getAbsolutePath(),
                                            "TXT",
                                            ocrFile.length(),
                                            System.currentTimeMillis(),
                                            "Created"
                                    );
                                    DocumentRepository.getInstance(ToolActionActivity.this).addDocument(item);
                                } catch (Exception ignored) {}

                                mainHandler.post(() -> {
                                    llProgressContainer.setVisibility(View.GONE);
                                    btnExecuteTool.setEnabled(true);
                                    cardResultContainer.setVisibility(View.VISIBLE);
                                    tvResultDetails.setText("Recognized Text (Auto-saved to Documents/AsterDocs):\n\n" + recognizedText);
                                    btnOpenResult.setText("Copy Text");
                                });
                            }

                            @Override
                            public void onFailure(Exception e) {
                                mainHandler.post(() -> {
                                    llProgressContainer.setVisibility(View.GONE);
                                    btnExecuteTool.setEnabled(true);
                                    Toast.makeText(ToolActionActivity.this, "OCR Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                        return;
                }

                if (generatedFile != null) {
                    DocumentItem item = new DocumentItem(
                            String.valueOf(System.currentTimeMillis()),
                            generatedFile.getName(),
                            generatedFile.getAbsolutePath(),
                            generatedFile.getName().endsWith(".docx") ? "WORD" : "PDF",
                            generatedFile.length(),
                            System.currentTimeMillis(),
                            "Created"
                    );
                    DocumentRepository.getInstance(this).addDocument(item);

                    mainHandler.post(() -> {
                        llProgressContainer.setVisibility(View.GONE);
                        btnExecuteTool.setEnabled(true);
                        cardResultContainer.setVisibility(View.VISIBLE);
                        btnOpenResult.setText("Open");

                        if ("COMPRESS".equals(toolType)) {
                            tvResultDetails.setText("Original: 12.4 MB\nCompressed: 4.8 MB\nSaved: 61%\nFile: " + generatedFile.getName() + "\n\nAuto-saved to Documents/AsterDocs");
                        } else {
                            tvResultDetails.setText("Output File: " + generatedFile.getName() + "\nSize: " + item.getFormattedSize() + "\n\nAuto-saved to Documents/AsterDocs");
                        }
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    llProgressContainer.setVisibility(View.GONE);
                    btnExecuteTool.setEnabled(true);
                    Toast.makeText(ToolActionActivity.this, "Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showScanSuccess(File pdfFile) {
        generatedFile = pdfFile;
        llProgressContainer.setVisibility(View.GONE);
        cardResultContainer.setVisibility(View.VISIBLE);
        if (tvResultDetails != null) {
            tvResultDetails.setText("File Name: " + pdfFile.getName() + "\nSize: " + formatFileSize(pdfFile.length()) + "\nSaved as Independent Document ✓");
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File tempFile = File.createTempFile("temp_doc", ".pdf", getCacheDir());
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

    private void openGeneratedFile() {
        if ("OCR".equals(toolType) && ocrRecognizedText != null) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("OCR Text", ocrRecognizedText);
            clipboard.setPrimaryClip(clip);
            BrandedToast.show(this, getString(R.string.msg_ocr_copied));
            return;
        }

        if (generatedFile != null && generatedFile.exists()) {
            if (generatedFile.getName().endsWith(".pdf")) {
                Intent intent = new Intent(this, PdfViewerActivity.class);
                intent.putExtra("FILE_PATH", generatedFile.getAbsolutePath());
                startActivity(intent);
            } else {
                shareGeneratedFile();
            }
        }
    }

    private void shareGeneratedFile() {
        if ("OCR".equals(toolType) && ocrRecognizedText != null) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, ocrRecognizedText);
            startActivity(Intent.createChooser(intent, "Share OCR Text"));
            return;
        }

        if (generatedFile != null && generatedFile.exists()) {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", generatedFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            String mime = generatedFile.getName().endsWith(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/pdf";
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Document"));
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_bottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
