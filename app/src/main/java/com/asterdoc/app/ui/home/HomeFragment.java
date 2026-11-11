package com.asterdoc.app.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.ui.files.CategoryFileListActivity;
import com.asterdoc.app.ui.tools.ToolActionActivity;
import com.asterdoc.app.ui.tools.ToolAdapter;
import com.asterdoc.app.ui.tools.ToolItem;
import com.asterdoc.app.ui.tools.arranger.PdfArrangerActivity;
import com.asterdoc.app.ui.tools.sign.SignPdfActivity;
import com.asterdoc.app.ui.viewer.ExcelViewerActivity;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.data.AsterineStorageManager;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ToolAdapter.OnToolClickListener {

    private DocumentRepository repository;
    private TextView tvCountAll, tvCountPdf, tvCountWord, tvCountExcel, tvCountTxt, tvCountTrash, tvCountFav;

    private final ActivityResultLauncher<Intent> phoneDocPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importAndOpenDocument(uri);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        repository = DocumentRepository.getInstance(requireContext());

        // Open Phone Document Card & FAB Actions
        View cardOpen = view.findViewById(R.id.cardOpenPhoneDocument);
        if (cardOpen != null) {
            cardOpen.setOnClickListener(v -> launchPhoneDocPicker());
        }

        View fabOpen = view.findViewById(R.id.fabOpenPhoneDoc);
        if (fabOpen != null) {
            fabOpen.setOnClickListener(v -> launchPhoneDocPicker());
        }

        LayoutAnimationController layoutAnim = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_down);

        // Tool Sections Setup
        RecyclerView rvConvert = view.findViewById(R.id.rvConvertTools);
        rvConvert.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvConvert.setAdapter(new ToolAdapter(getConvertTools(), this));
        rvConvert.setLayoutAnimation(layoutAnim);

        RecyclerView rvEdit = view.findViewById(R.id.rvEditTools);
        rvEdit.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvEdit.setAdapter(new ToolAdapter(getEditTools(), this));
        rvEdit.setLayoutAnimation(layoutAnim);

        RecyclerView rvScan = view.findViewById(R.id.rvScanTools);
        rvScan.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvScan.setAdapter(new ToolAdapter(getScanTools(), this));
        rvScan.setLayoutAnimation(layoutAnim);

        // Bind Category Card Count TextViews
        tvCountAll = view.findViewById(R.id.tvCountAll);
        tvCountPdf = view.findViewById(R.id.tvCountPdf);
        tvCountWord = view.findViewById(R.id.tvCountWord);
        tvCountExcel = view.findViewById(R.id.tvCountExcel);
        tvCountTxt = view.findViewById(R.id.tvCountTxt);
        tvCountTrash = view.findViewById(R.id.tvCountTrash);
        tvCountFav = view.findViewById(R.id.tvCountFav);

        // Bind Category Card Click Listeners
        View cardCatAll = view.findViewById(R.id.cardCatAll);
        View cardCatPdf = view.findViewById(R.id.cardCatPdf);
        View cardCatWord = view.findViewById(R.id.cardCatWord);
        View cardCatExcel = view.findViewById(R.id.cardCatExcel);
        View cardCatTxt = view.findViewById(R.id.cardCatTxt);
        View cardCatTrash = view.findViewById(R.id.cardCatTrash);
        View cardCatFav = view.findViewById(R.id.cardCatFav);

        if (cardCatAll != null) cardCatAll.setOnClickListener(v -> openCategoryFileList("ALL"));
        if (cardCatPdf != null) cardCatPdf.setOnClickListener(v -> openCategoryFileList("PDF"));
        if (cardCatWord != null) cardCatWord.setOnClickListener(v -> openCategoryFileList("WORD"));
        if (cardCatExcel != null) cardCatExcel.setOnClickListener(v -> openCategoryFileList("EXCEL"));
        if (cardCatTxt != null) cardCatTxt.setOnClickListener(v -> openCategoryFileList("TXT"));
        if (cardCatTrash != null) cardCatTrash.setOnClickListener(v -> openCategoryFileList("TRASH"));
        if (cardCatFav != null) cardCatFav.setOnClickListener(v -> openCategoryFileList("FAVORITES"));

        updateCategoryCards();

        View nativeContainer = view.findViewById(R.id.nativeAdContainerHome);
        if (nativeContainer != null) {
            com.asterdoc.app.ads.AdManager.loadNativeAd(requireContext(), (ViewGroup) nativeContainer);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCategoryCards();
        if (getView() != null) {
            RecyclerView rvConvert = getView().findViewById(R.id.rvConvertTools);
            RecyclerView rvEdit = getView().findViewById(R.id.rvEditTools);
            RecyclerView rvScan = getView().findViewById(R.id.rvScanTools);
            if (rvConvert != null) rvConvert.scheduleLayoutAnimation();
            if (rvEdit != null) rvEdit.scheduleLayoutAnimation();
            if (rvScan != null) rvScan.scheduleLayoutAnimation();
        }
    }

    private void updateCategoryCards() {
        if (repository == null) return;

        List<DocumentItem> allDocs = repository.getAllDocuments();
        int countAll = allDocs.size();
        int countPdf = 0;
        int countWord = 0;
        int countExcel = 0;
        int countTxt = 0;
        int countFav = 0;

        for (DocumentItem item : allDocs) {
            if (item.isFavorite()) countFav++;

            String type = item.getType() != null ? item.getType().toUpperCase() : "";
            String name = item.getName() != null ? item.getName().toLowerCase() : "";

            if ("PDF".equals(type) || name.endsWith(".pdf")) {
                countPdf++;
            } else if ("WORD".equals(type) || name.endsWith(".docx") || name.endsWith(".doc")) {
                countWord++;
            } else if ("EXCEL".equals(type) || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                countExcel++;
            } else if ("TXT".equals(type) || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) {
                countTxt++;
            }
        }

        if (tvCountAll != null) tvCountAll.setText(String.valueOf(countAll));
        if (tvCountPdf != null) tvCountPdf.setText(String.valueOf(countPdf));
        if (tvCountWord != null) tvCountWord.setText(String.valueOf(countWord));
        if (tvCountExcel != null) tvCountExcel.setText(String.valueOf(countExcel));
        if (tvCountTxt != null) tvCountTxt.setText(String.valueOf(countTxt));
        if (tvCountFav != null) tvCountFav.setText(String.valueOf(countFav));
        if (tvCountTrash != null) tvCountTrash.setText(String.valueOf(repository.getTrashDocuments().size()));
    }

    private void openCategoryFileList(String category) {
        Intent intent = new Intent(requireContext(), CategoryFileListActivity.class);
        intent.putExtra("CATEGORY_TYPE", category);
        startActivity(intent);
    }

    private void launchPhoneDocPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimetypes = {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/plain"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        phoneDocPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_doc_picker)));
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }

        if (result == null || result.startsWith("open_") || result.startsWith("app_open_") || result.startsWith("document_") || !result.contains(".")) {
            String mimeType = requireContext().getContentResolver().getType(uri);
            String ext = ".pdf";
            String prefix = "Document_";
            if (mimeType != null) {
                if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) {
                    ext = ".docx";
                    prefix = "Word_Document_";
                } else if (mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("csv")) {
                    ext = ".xlsx";
                    prefix = "Spreadsheet_";
                } else if (mimeType.contains("text")) {
                    ext = ".txt";
                    prefix = "Text_Document_";
                }
            }
            if (result == null || result.trim().isEmpty()) {
                result = prefix + System.currentTimeMillis() + ext;
            } else {
                int dot = result.lastIndexOf('.');
                if (dot != -1) {
                    result = prefix + System.currentTimeMillis() + result.substring(dot);
                } else {
                    result = result + ext;
                }
            }
        }
        return result;
    }

    private void importAndOpenDocument(Uri uri) {
        if (uri == null) {
            BrandedToast.show(requireContext(), getString(R.string.msg_import_error));
            return;
        }

        try {
            String fileName = getFileNameFromUri(uri);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "Document_" + System.currentTimeMillis() + ".pdf";
            }
            String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String lower = safeFileName.toLowerCase();

            if (lower.endsWith(".pptx") || lower.endsWith(".ppt") || lower.endsWith(".pps") || lower.endsWith(".ppsx")) {
                BrandedToast.show(requireContext(), "PowerPoint files are currently not supported by Asterine.");
                return;
            }

            String type = "PDF";
            if (lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".rtf") || lower.endsWith(".odt")) {
                type = "WORD";
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
                type = "EXCEL";
            } else if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md") || lower.endsWith(".xml") || lower.endsWith(".json")) {
                type = "TXT";
            }

            File importedFile = AsterineStorageManager.getFileForOperation(requireContext(), type, safeFileName);
            if (importedFile == null) {
                importedFile = new File(requireContext().getCacheDir(), safeFileName);
            }

            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(importedFile)) {
                if (is == null) {
                    BrandedToast.show(requireContext(), getString(R.string.msg_import_error));
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }

            if (!importedFile.exists() || importedFile.length() == 0) {
                BrandedToast.show(requireContext(), getString(R.string.msg_import_error));
                return;
            }

            DocumentItem item = new DocumentItem(
                    String.valueOf(System.currentTimeMillis()),
                    fileName,
                    importedFile.getAbsolutePath(),
                    type,
                    importedFile.length(),
                    System.currentTimeMillis(),
                    "Recent"
            );
            if (repository != null) {
                repository.addDocument(item);
            }

            if ("EXCEL".equals(type)) {
                Intent intent = new Intent(requireContext(), ExcelViewerActivity.class);
                intent.putExtra("FILE_PATH", importedFile.getAbsolutePath());
                startActivity(intent);
            } else {
                Intent intent = new Intent(requireContext(), PdfViewerActivity.class);
                intent.putExtra("FILE_PATH", importedFile.getAbsolutePath());
                startActivity(intent);
            }

        } catch (Exception e) {
            Log.e("ImportError", "Failed to import document", e);
            BrandedToast.show(requireContext(), getString(R.string.msg_import_error) + " " + e.getMessage());
        }
    }



    private List<ToolItem> getScanTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("s1", getString(R.string.scan_document), android.R.drawable.ic_menu_camera, "SCAN"));
        return list;
    }

    private List<ToolItem> getConvertTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("c2", getString(R.string.word_to_pdf), android.R.drawable.ic_menu_save, "WORD_PDF"));
        list.add(new ToolItem("c3", getString(R.string.excel_to_pdf), android.R.drawable.ic_menu_sort_by_size, "EXCEL_PDF"));
        list.add(new ToolItem("c5", getString(R.string.image_to_pdf), android.R.drawable.ic_menu_gallery, "IMAGES_PDF"));
        return list;
    }

    private List<ToolItem> getEditTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("e1", getString(R.string.merge_pdf), android.R.drawable.ic_menu_add, "MERGE"));
        list.add(new ToolItem("e2", getString(R.string.split_pdf), android.R.drawable.ic_menu_crop, "SPLIT"));
        list.add(new ToolItem("e4", getString(R.string.sign_pdf), android.R.drawable.ic_menu_send, "SIGN"));
        list.add(new ToolItem("e5", getString(R.string.page_arranger), android.R.drawable.ic_menu_sort_alphabetically, "ARRANGE"));
        return list;
    }

    @Override
    public void onToolClick(ToolItem item) {
        String type = item.getToolType();
        if (type == null) type = "";
        final String toolType = type;

        Runnable launchTool = () -> {
            Intent intent;
            if ("ARRANGE".equals(toolType)) {
                intent = new Intent(requireContext(), PdfArrangerActivity.class);
            } else if ("SIGN".equals(toolType)) {
                intent = new Intent(requireContext(), SignPdfActivity.class);
            } else {
                intent = new Intent(requireContext(), ToolActionActivity.class);
                intent.putExtra("TOOL_TYPE", toolType);
                intent.putExtra("TOOL_TITLE", item.getTitle());
            }
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out);
            }
        };

        if ("WORD_PDF".equals(toolType) || "SIGN".equals(toolType) || "EXCEL_PDF".equals(toolType) || "MERGE".equals(toolType) || "ARRANGE".equals(toolType)) {
            com.asterdoc.app.ads.AdManager.showInterstitial(requireActivity(), item.getTitle(), launchTool);
        } else {
            launchTool.run();
        }
    }
}

