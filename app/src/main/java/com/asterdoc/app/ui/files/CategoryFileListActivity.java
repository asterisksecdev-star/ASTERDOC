package com.asterdoc.app.ui.files;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.pdf.SamplePdfGenerator;
import com.asterdoc.app.ui.home.DocumentAdapter;
import com.asterdoc.app.ui.viewer.ExcelViewerActivity;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class CategoryFileListActivity extends AppCompatActivity implements DocumentAdapter.OnDocumentClickListener {

    private DocumentRepository repository;
    private DocumentAdapter documentAdapter;
    private RecyclerView rvCategoryFiles;
    private TextView tvEmptyCategoryFiles;
    private TextView tvCategoryTitle;

    private View layoutCatSearchBar;
    private EditText etCatSearchQuery;

    private String categoryType = "ALL";
    private List<DocumentItem> currentCategoryItems = new ArrayList<>();
    private int currentSortType = 0; // 0 = Date desc, 1 = Name asc, 2 = Size desc

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_file_list);

        repository = DocumentRepository.getInstance(this);
        repository.addListener(this::loadCategoryDocuments);

        if (getIntent() != null && getIntent().hasExtra("CATEGORY_TYPE")) {
            categoryType = getIntent().getStringExtra("CATEGORY_TYPE");
        }

        initViews();
        loadCategoryDocuments();
        com.asterdoc.app.ads.AdManager.loadBannerAd(this, findViewById(R.id.adContainerCategory));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategoryDocuments();
    }

    private void initViews() {
        View btnCatBack = findViewById(R.id.btnCatBack);
        if (btnCatBack != null) btnCatBack.setOnClickListener(v -> finish());

        tvCategoryTitle = findViewById(R.id.tvCategoryTitle);
        setActivityTitle(categoryType);

        ImageButton btnCatSelectAll = findViewById(R.id.btnCatSelectAll);
        ImageButton btnCatSort = findViewById(R.id.btnCatSort);
        ImageButton btnCatSearch = findViewById(R.id.btnCatSearch);

        layoutCatSearchBar = findViewById(R.id.layoutCatSearchBar);
        etCatSearchQuery = findViewById(R.id.etCatSearchQuery);
        ImageButton btnCatCloseSearch = findViewById(R.id.btnCatCloseSearch);

        View cardEmptyTrash = findViewById(R.id.cardEmptyTrash);
        View btnActivityEmptyTrash = findViewById(R.id.btnActivityEmptyTrash);

        if ("TRASH".equalsIgnoreCase(categoryType)) {
            if (cardEmptyTrash != null) cardEmptyTrash.setVisibility(View.VISIBLE);
        } else {
            if (cardEmptyTrash != null) cardEmptyTrash.setVisibility(View.GONE);
        }

        if (btnActivityEmptyTrash != null) {
            btnActivityEmptyTrash.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.empty_trash_title)
                        .setMessage(R.string.empty_trash_msg)
                        .setIcon(R.drawable.asterine_logo_image)
                        .setPositiveButton(R.string.empty_trash, (dialog, which) -> {
                            repository.emptyTrash();
                            loadCategoryDocuments();
                            BrandedToast.show(this, getString(R.string.msg_trash_emptied));
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }

        if (btnCatSearch != null) {
            btnCatSearch.setOnClickListener(v -> toggleSearchBar());
        }

        if (btnCatCloseSearch != null) {
            btnCatCloseSearch.setOnClickListener(v -> closeSearchBar());
        }

        if (btnCatSort != null) {
            btnCatSort.setOnClickListener(v -> showSortMenu(v));
        }

        if (btnCatSelectAll != null) {
            btnCatSelectAll.setOnClickListener(v -> {
                if (documentAdapter != null) {
                    documentAdapter.toggleSelectAll();
                }
            });
        }

        View btnBulkSelectAll = findViewById(R.id.btnBulkSelectAll);
        View btnBulkRestore = findViewById(R.id.btnBulkRestore);
        View btnBulkShare = findViewById(R.id.btnBulkShare);
        View btnBulkDelete = findViewById(R.id.btnBulkDelete);
        View btnBulkClose = findViewById(R.id.btnBulkClose);

        if (btnBulkSelectAll != null) btnBulkSelectAll.setOnClickListener(v -> documentAdapter.toggleSelectAll());
        if (btnBulkClose != null) btnBulkClose.setOnClickListener(v -> documentAdapter.clearSelection());
        if (btnBulkRestore != null) btnBulkRestore.setOnClickListener(v -> bulkRestoreSelected());
        if (btnBulkDelete != null) btnBulkDelete.setOnClickListener(v -> bulkDeleteSelected());
        if (btnBulkShare != null) btnBulkShare.setOnClickListener(v -> bulkShareSelected());

        if (etCatSearchQuery != null) {
            etCatSearchQuery.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterCurrentListByQuery(s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        rvCategoryFiles = findViewById(R.id.rvCategoryFiles);
        tvEmptyCategoryFiles = findViewById(R.id.tvEmptyCategoryFiles);

        rvCategoryFiles.setHasFixedSize(true);
        rvCategoryFiles.setItemViewCacheSize(30);
        rvCategoryFiles.setLayoutManager(new LinearLayoutManager(this));
        documentAdapter = new DocumentAdapter(new ArrayList<>(), this);
        rvCategoryFiles.setAdapter(documentAdapter);
    }

    private void setActivityTitle(String type) {
        if (tvCategoryTitle == null) return;
        switch (type.toUpperCase()) {
            case "PDF":
                tvCategoryTitle.setText(R.string.pdf_files);
                break;
            case "WORD":
                tvCategoryTitle.setText(R.string.word_files);
                break;
            case "EXCEL":
                tvCategoryTitle.setText(R.string.excel_files);
                break;
            case "PPT":
                tvCategoryTitle.setText("PPT Files");
                break;
            case "TXT":
                tvCategoryTitle.setText(R.string.txt_files);
                break;
            case "FAVORITES":
                tvCategoryTitle.setText(R.string.favorite_files);
                break;
            case "TRASH":
                tvCategoryTitle.setText(R.string.trash_title);
                break;
            case "DIRECTORIES":
            case "ALL":
            default:
                tvCategoryTitle.setText(R.string.all_files);
                break;
        }
    }



    private void toggleSearchBar() {
        if (layoutCatSearchBar != null) {
            if (layoutCatSearchBar.getVisibility() == View.VISIBLE) {
                closeSearchBar();
            } else {
                layoutCatSearchBar.setVisibility(View.VISIBLE);
                if (etCatSearchQuery != null) etCatSearchQuery.requestFocus();
            }
        }
    }

    private void closeSearchBar() {
        if (layoutCatSearchBar != null) {
            layoutCatSearchBar.setVisibility(View.GONE);
            if (etCatSearchQuery != null) etCatSearchQuery.setText("");
            applySortingAndDisplay();
        }
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.sort_by_date));
        popup.getMenu().add(0, 2, 1, getString(R.string.sort_by_name));
        popup.getMenu().add(0, 3, 2, getString(R.string.sort_by_size));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) currentSortType = 0;
            else if (id == 2) currentSortType = 1;
            else if (id == 3) currentSortType = 2;
            applySortingAndDisplay();
            return true;
        });
        popup.show();
    }

    private void loadCategoryDocuments() {
        long start = System.currentTimeMillis();
        boolean isMainThread = Looper.getMainLooper().isCurrentThread();
        Trace.beginSection("CategoryFileList_LoadAndFilter_" + categoryType);

        if (repository == null) {
            Trace.endSection();
            return;
        }
        List<DocumentItem> all = repository.getAllDocuments();
        currentCategoryItems.clear();

        String type = categoryType.toUpperCase();
        switch (type) {
            case "PDF":
                for (DocumentItem item : all) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("PDF".equalsIgnoreCase(item.getType()) || name.endsWith(".pdf")) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "WORD":
                for (DocumentItem item : all) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("WORD".equalsIgnoreCase(item.getType()) || name.endsWith(".docx") || name.endsWith(".doc")) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "EXCEL":
                for (DocumentItem item : all) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("EXCEL".equalsIgnoreCase(item.getType()) || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "PPT":
                for (DocumentItem item : all) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("PPT".equalsIgnoreCase(item.getType()) || name.endsWith(".pptx") || name.endsWith(".ppt")) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "TXT":
                for (DocumentItem item : all) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("TXT".equalsIgnoreCase(item.getType()) || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "FAVORITES":
                for (DocumentItem item : all) {
                    if (item.isFavorite()) {
                        currentCategoryItems.add(item);
                    }
                }
                break;
            case "TRASH":
                currentCategoryItems.addAll(repository.getTrashDocuments());
                if (documentAdapter != null) documentAdapter.setTrashMode(true);
                break;
            case "DIRECTORIES":
            case "ALL":
            default:
                currentCategoryItems.addAll(all);
                break;
        }

        applySortingAndDisplay();

        long elapsed = System.currentTimeMillis() - start;
        Log.d("ASTERINE_PERF", "Operation: LoadCategoryDocuments (" + categoryType + ") | Start: " + start + " | End: " + System.currentTimeMillis() + " | Elapsed: " + elapsed + "ms | MainThread: " + isMainThread + " | FileScan/Parse Triggered: false");
        Trace.endSection();
    }

    private void applySortingAndDisplay() {
        if (currentSortType == 1) {
            currentCategoryItems.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } else if (currentSortType == 2) {
            currentCategoryItems.sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
        } else {
            currentCategoryItems.sort((a, b) -> Long.compare(b.getDate(), a.getDate()));
        }

        if (documentAdapter != null) {
            documentAdapter.updateData(currentCategoryItems);
        }

        if (currentCategoryItems.isEmpty()) {
            if (tvEmptyCategoryFiles != null) {
                tvEmptyCategoryFiles.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_empty_man_folder, 0, 0);
                tvEmptyCategoryFiles.setCompoundDrawablePadding(32);
                tvEmptyCategoryFiles.setText(R.string.no_files_yet);
                tvEmptyCategoryFiles.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
                tvEmptyCategoryFiles.setTextSize(18);
                tvEmptyCategoryFiles.setTypeface(null, android.graphics.Typeface.BOLD);
                tvEmptyCategoryFiles.setVisibility(View.VISIBLE);
            }
            if (rvCategoryFiles != null) rvCategoryFiles.setVisibility(View.GONE);
        } else {
            if (tvEmptyCategoryFiles != null) tvEmptyCategoryFiles.setVisibility(View.GONE);
            if (rvCategoryFiles != null) rvCategoryFiles.setVisibility(View.VISIBLE);
        }
    }

    private void filterCurrentListByQuery(String query) {
        if (query.isEmpty()) {
            applySortingAndDisplay();
            return;
        }
        List<DocumentItem> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (DocumentItem item : currentCategoryItems) {
            if (item.getName().toLowerCase().contains(q)) {
                filtered.add(item);
            }
        }
        if (documentAdapter != null) {
            documentAdapter.updateData(filtered);
        }
    }

    @Override
    public void onDocumentClick(DocumentItem item) {
        openDocument(item);
    }

    @Override
    public void onDocumentLongClick(DocumentItem item) {}

    @Override
    public void onSelectionChanged(int selectedCount) {
        View llBulkActions = findViewById(R.id.llBulkActions);
        TextView tvSelectedCount = findViewById(R.id.tvSelectedCount);
        if (llBulkActions != null) {
            if (selectedCount > 0) {
                llBulkActions.setVisibility(View.VISIBLE);
                if (tvSelectedCount != null) {
                    tvSelectedCount.setText(selectedCount + " selected");
                }
                View btnShare = findViewById(R.id.btnBulkShare);
                View btnDelete = findViewById(R.id.btnBulkDelete);
                View btnRestore = findViewById(R.id.btnBulkRestore);

                if ("TRASH".equalsIgnoreCase(categoryType)) {
                    if (btnShare != null) btnShare.setVisibility(View.GONE);
                    if (btnDelete != null) btnDelete.setVisibility(View.VISIBLE);
                    if (btnRestore != null) btnRestore.setVisibility(View.VISIBLE);
                } else {
                    if (btnShare != null) btnShare.setVisibility(View.VISIBLE);
                    if (btnDelete != null) btnDelete.setVisibility(View.VISIBLE);
                    if (btnRestore != null) btnRestore.setVisibility(View.GONE);
                }
            } else {
                llBulkActions.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onFavoriteClick(DocumentItem item) {
        if (repository != null && documentAdapter != null) {
            item.setFavorite(!item.isFavorite());
            repository.updateDocument(item);
            loadCategoryDocuments();
            BrandedToast.show(this, getString(item.isFavorite() ? R.string.msg_added_favorites : R.string.msg_removed_favorites));
        }
    }

    @Override
    public void onMoreClick(DocumentItem item, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        if ("TRASH".equalsIgnoreCase(categoryType)) {
            popup.getMenu().add(0, 10, 0, "Restore");
            popup.getMenu().add(0, 11, 1, "Delete Permanently");

            popup.setOnMenuItemClickListener(menuItem -> {
                int itemId = menuItem.getItemId();
                if (itemId == 10) {
                    item.setTrash(false);
                    repository.updateDocument(item);
                    loadCategoryDocuments();
                    BrandedToast.show(this, "Document restored!");
                    return true;
                } else if (itemId == 11) {
                    repository.deletePermanently(item.getId());
                    loadCategoryDocuments();
                    BrandedToast.show(this, "Deleted permanently!");
                    return true;
                }
                return false;
            });
        } else {
            popup.getMenu().add(0, 1, 0, getString(R.string.menu_open));
            popup.getMenu().add(0, 2, 0, getString(R.string.menu_share));
            popup.getMenu().add(0, 3, 0, getString(R.string.menu_rename));
            popup.getMenu().add(0, 4, 0, getString(R.string.menu_move_trash));

            popup.setOnMenuItemClickListener(menuItem -> {
                int itemId = menuItem.getItemId();
                if (itemId == 1) {
                    openDocument(item);
                    return true;
                } else if (itemId == 2) {
                    shareDocument(item);
                    return true;
                } else if (itemId == 3) {
                    renameDocumentDialog(item);
                    return true;
                } else if (itemId == 4) {
                    confirmMoveToTrash(item);
                    return true;
                }
                return false;
            });
        }
        popup.show();
    }

    private void bulkRestoreSelected() {
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;
        Set<String> keysToProcess = new HashSet<>(selectedKeys);
        int count = 0;
        List<DocumentItem> copyList = new ArrayList<>(currentCategoryItems);
        documentAdapter.clearSelection();
        for (DocumentItem item : copyList) {
            String key = item.getId() != null ? item.getId() : item.getName();
            if (keysToProcess.contains(key)) {
                item.setTrash(false);
                repository.updateDocument(item);
                count++;
            }
        }
        loadCategoryDocuments();
        BrandedToast.show(this, count + " documents restored!");
    }

    private void bulkDeleteSelected() {
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;
        Set<String> keysToProcess = new HashSet<>(selectedKeys);
        int count = keysToProcess.size();
        boolean isTrash = "TRASH".equalsIgnoreCase(categoryType);
        String title = isTrash ? "Delete Permanently" : "Move to Trash";
        String msg = isTrash ? "Delete permanently " + count + " files?" : "Move " + count + " files to trash?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    List<DocumentItem> targetItems = new ArrayList<>(currentCategoryItems);
                    documentAdapter.clearSelection();
                    for (DocumentItem item : targetItems) {
                        String key = item.getId() != null ? item.getId() : item.getName();
                        if (keysToProcess.contains(key)) {
                            if (isTrash) {
                                repository.deletePermanently(item.getId());
                            } else {
                                repository.moveToTrash(item.getId());
                            }
                        }
                    }
                    loadCategoryDocuments();
                    BrandedToast.show(this, count + " files processed.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void bulkShareSelected() {
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (DocumentItem item : currentCategoryItems) {
            String key = item.getId() != null ? item.getId() : item.getName();
            if (selectedKeys.contains(key)) {
                File file = new File(item.getPath());
                if (file.exists()) {
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                    uris.add(uri);
                }
            }
        }
        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Files"));
        }
    }

    private void openDocument(DocumentItem item) {
        File file = new File(item.getPath());
        if (!file.exists() || file.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.file_not_found_title)
                    .setMessage(getString(R.string.file_not_found_msg, item.getName()))
                    .setIcon(R.drawable.asterine_logo_image)
                    .setPositiveButton(R.string.btn_delete_from_app, (dialog, which) -> {
                        if (repository != null) {
                            repository.deletePermanently(item.getId());
                            loadCategoryDocuments();
                        }
                        BrandedToast.show(this, getString(R.string.msg_document_deleted));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
            Intent intent = new Intent(this, ExcelViewerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            startActivity(intent);
            return;
        }

        if (name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp") || name.endsWith(".ppsx")) {
            BrandedToast.show(this, "PowerPoint files are currently not supported by Asterine.");
            return;
        }

        if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") ||
            name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")) {
            BrandedToast.show(this, getString(R.string.msg_prep_doc_view));
            final File targetFile = file;
            Executors.newSingleThreadExecutor().execute(() -> {
                File viewableFile = targetFile;
                try {
                    if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt")) {
                        viewableFile = PdfProcessor.wordToPdf(this, targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                    } else {
                        viewableFile = PdfProcessor.textToPdf(this, targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                    }
                } catch (Exception ignored) {}

                final File finalViewable = viewableFile;
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, PdfViewerActivity.class);
                    intent.putExtra("FILE_PATH", finalViewable.getAbsolutePath());
                    startActivity(intent);
                });
            });
        } else {
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            startActivity(intent);
        }
    }

    private void shareDocument(DocumentItem item) {
        File file = new File(item.getPath());
        if (!file.exists() || file.length() == 0) {
            file = SamplePdfGenerator.createSamplePdf(this, item.getName(), item.getName());
            item.setPath(file.getAbsolutePath());
            item.setSize(file.length());
            if (repository != null) {
                repository.updateDocument(item);
            }
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.menu_share)));
    }

    private void renameDocumentDialog(DocumentItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.rename_document);
        builder.setIcon(R.drawable.asterine_logo_image);

        final EditText input = new EditText(this);
        input.setPadding(48, 32, 48, 32);

        String fullName = item.getName() != null ? item.getName() : "Document.pdf";
        String baseName = fullName;
        String extension = "";
        int dotIndex = fullName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = fullName.substring(0, dotIndex);
            extension = fullName.substring(dotIndex);
        }

        input.setText(baseName);
        input.setSelection(baseName.length());
        builder.setView(input);

        final String finalExtension = extension;
        builder.setPositiveButton(R.string.rename, (dialog, which) -> {
            String newBaseName = input.getText().toString().trim();
            if (!newBaseName.isEmpty()) {
                String newName = newBaseName + finalExtension;
                item.setName(newName);

                try {
                    File oldFile = new File(item.getPath());
                    if (oldFile.exists()) {
                        File newFile = new File(oldFile.getParentFile(), newName);
                        if (oldFile.renameTo(newFile)) {
                            item.setPath(newFile.getAbsolutePath());
                        }
                    }
                } catch (Exception ignored) {}

                if (repository != null) {
                    repository.updateDocument(item);
                    loadCategoryDocuments();
                }
                BrandedToast.show(this, getString(R.string.msg_doc_renamed));
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void confirmMoveToTrash(DocumentItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.move_to_trash_title)
                .setMessage(R.string.move_to_trash_msg)
                .setIcon(R.drawable.asterine_logo_image)
                .setPositiveButton(R.string.move_to_trash_btn, (dialog, which) -> {
                    if (repository != null) {
                        repository.moveToTrash(item.getId());
                        loadCategoryDocuments();
                        BrandedToast.show(this, getString(R.string.msg_moved_trash));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.removeListener(this::loadCategoryDocuments);
        }
    }
}

