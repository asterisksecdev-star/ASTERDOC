package com.asterdoc.app.ui.files;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
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
import com.asterdoc.app.data.AsterineStorageManager;
import android.util.Log;
import java.util.HashSet;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class FilesFragment extends Fragment implements DocumentAdapter.OnDocumentClickListener {

    private DocumentRepository repository;
    private DocumentAdapter adapter;
    private RecyclerView rvFilesList;
    private TextView tvEmptyFiles;
    private View llBulkActions;
    private TextView tvSelectedCount;
    private int selectedTabPosition = 0;
    private View layoutFilesSearchBar;
    private EditText etFilesSearchQuery;
    private int currentSortType = 0; // 0 = Date desc, 1 = Name asc, 2 = Size desc

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

    private void launchPhoneDocPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimetypes = {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
                "text/plain"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        phoneDocPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.open_phone_doc)));
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
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
        return result != null ? result : "Document_" + System.currentTimeMillis() + ".pdf";
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
                refreshList();
            }

            openDocument(item);

        } catch (Exception e) {
            Log.e("ImportError", "Failed to import document", e);
            BrandedToast.show(requireContext(), getString(R.string.msg_import_error) + " " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        repository = DocumentRepository.getInstance(requireContext());
        repository.addListener(this::refreshList);

        rvFilesList = view.findViewById(R.id.rvFilesList);
        tvEmptyFiles = view.findViewById(R.id.tvEmptyFiles);
        llBulkActions = view.findViewById(R.id.llBulkActions);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);

        View fabOpen = view.findViewById(R.id.fabOpenPhoneDocFiles);
        if (fabOpen != null) {
            fabOpen.setOnClickListener(v -> launchPhoneDocPicker());
        }
        View btnBulkSelectAll = view.findViewById(R.id.btnBulkSelectAll);
        View btnBulkRestore = view.findViewById(R.id.btnBulkRestore);
        View btnBulkShare = view.findViewById(R.id.btnBulkShare);
        View btnBulkDelete = view.findViewById(R.id.btnBulkDelete);
        View btnBulkClose = view.findViewById(R.id.btnBulkClose);

        if (btnBulkSelectAll != null) btnBulkSelectAll.setOnClickListener(v -> adapter.toggleSelectAll());
        if (btnBulkRestore != null) btnBulkRestore.setOnClickListener(v -> bulkRestoreSelected());
        if (btnBulkShare != null) btnBulkShare.setOnClickListener(v -> bulkShareSelected());
        if (btnBulkDelete != null) btnBulkDelete.setOnClickListener(v -> bulkDeleteSelected());
        if (btnBulkClose != null) btnBulkClose.setOnClickListener(v -> adapter.clearSelection());

        View btnFilesSelectAll = view.findViewById(R.id.btnFilesSelectAll);
        View btnFilesSort = view.findViewById(R.id.btnFilesSort);
        View btnFilesSearch = view.findViewById(R.id.btnFilesSearch);
        layoutFilesSearchBar = view.findViewById(R.id.layoutFilesSearchBar);
        etFilesSearchQuery = view.findViewById(R.id.etFilesSearchQuery);
        View btnFilesCloseSearch = view.findViewById(R.id.btnFilesCloseSearch);

        if (btnFilesSelectAll != null) btnFilesSelectAll.setOnClickListener(v -> {
            if (adapter != null) adapter.toggleSelectAll();
        });
        if (btnFilesSort != null) btnFilesSort.setOnClickListener(v -> showSortMenu(v));
        if (btnFilesSearch != null) btnFilesSearch.setOnClickListener(v -> toggleSearchBar());
        if (btnFilesCloseSearch != null) btnFilesCloseSearch.setOnClickListener(v -> closeSearchBar());

        if (etFilesSearchQuery != null) {
            etFilesSearchQuery.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    refreshList();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        TabLayout tabLayout = view.findViewById(R.id.tabLayoutFiles);

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.all)));
        tabLayout.addTab(tabLayout.newTab().setText("PDF"));
        tabLayout.addTab(tabLayout.newTab().setText("Word"));
        tabLayout.addTab(tabLayout.newTab().setText("Excel"));
        tabLayout.addTab(tabLayout.newTab().setText("TXT"));

        rvFilesList.setHasFixedSize(true);
        rvFilesList.setItemViewCacheSize(30);
        rvFilesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFilesList.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_down));
        adapter = new DocumentAdapter(getFilteredList(), this);
        rvFilesList.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTabPosition = tab.getPosition();
                refreshList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        refreshList();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private List<DocumentItem> getFilteredList() {
        List<DocumentItem> allDocs = repository.getAllDocuments();
        List<DocumentItem> filtered = new ArrayList<>();
        switch (selectedTabPosition) {
            case 1:
                for (DocumentItem item : allDocs) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("PDF".equalsIgnoreCase(item.getType()) || name.endsWith(".pdf")) filtered.add(item);
                }
                return filtered;
            case 2:
                for (DocumentItem item : allDocs) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("WORD".equalsIgnoreCase(item.getType()) || name.endsWith(".docx") || name.endsWith(".doc")) filtered.add(item);
                }
                return filtered;
            case 3:
                for (DocumentItem item : allDocs) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("EXCEL".equalsIgnoreCase(item.getType()) || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) filtered.add(item);
                }
                return filtered;
            case 4:
                for (DocumentItem item : allDocs) {
                    String name = item.getName() != null ? item.getName().toLowerCase() : "";
                    if ("TXT".equalsIgnoreCase(item.getType()) || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) filtered.add(item);
                }
                return filtered;
            case 0:
            default:
                return allDocs;
        }
    }

    private void refreshList() {
        if (adapter != null) {
            adapter.clearSelection();
        }
        List<DocumentItem> list = getFilteredList();
        applySorting(list);

        String query = etFilesSearchQuery != null ? etFilesSearchQuery.getText().toString().trim().toLowerCase() : "";
        if (!query.isEmpty()) {
            List<DocumentItem> searched = new ArrayList<>();
            for (DocumentItem item : list) {
                if (item.getName().toLowerCase().contains(query)) {
                    searched.add(item);
                }
            }
            list = searched;
        }

        updateUI(list);
    }



    private void toggleSearchBar() {
        if (layoutFilesSearchBar != null) {
            if (layoutFilesSearchBar.getVisibility() == View.VISIBLE) {
                closeSearchBar();
            } else {
                layoutFilesSearchBar.setVisibility(View.VISIBLE);
                if (etFilesSearchQuery != null) etFilesSearchQuery.requestFocus();
            }
        }
    }

    private void closeSearchBar() {
        if (layoutFilesSearchBar != null) {
            layoutFilesSearchBar.setVisibility(View.GONE);
            if (etFilesSearchQuery != null) etFilesSearchQuery.setText("");
            refreshList();
        }
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.sort_by_date));
        popup.getMenu().add(0, 2, 1, getString(R.string.sort_by_name));
        popup.getMenu().add(0, 3, 2, getString(R.string.sort_by_size));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) currentSortType = 0;
            else if (id == 2) currentSortType = 1;
            else if (id == 3) currentSortType = 2;
            refreshList();
            return true;
        });
        popup.show();
    }

    private void applySorting(List<DocumentItem> list) {
        if (currentSortType == 1) {
            java.util.Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } else if (currentSortType == 2) {
            java.util.Collections.sort(list, (a, b) -> Long.compare(b.getSize(), a.getSize()));
        } else {
            java.util.Collections.sort(list, (a, b) -> Long.compare(b.getDate(), a.getDate()));
        }
    }

    private void updateUI(List<DocumentItem> list) {
        adapter.updateData(list);
        if (list.isEmpty()) {
            tvEmptyFiles.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_empty_man_folder, 0, 0);
            tvEmptyFiles.setCompoundDrawablePadding(32);
            tvEmptyFiles.setText(R.string.no_files_yet);
            tvEmptyFiles.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            tvEmptyFiles.setTextSize(18);
            tvEmptyFiles.setTypeface(null, android.graphics.Typeface.BOLD);
            tvEmptyFiles.setVisibility(View.VISIBLE);
            rvFilesList.setVisibility(View.GONE);
        } else {
            tvEmptyFiles.setVisibility(View.GONE);
            rvFilesList.setVisibility(View.VISIBLE);
            rvFilesList.scheduleLayoutAnimation();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (repository != null) {
            repository.removeListener(this::refreshList);
        }
    }

    @Override
    public void onDocumentClick(DocumentItem item) {
        openDocument(item);
    }

    @Override
    public void onDocumentLongClick(DocumentItem item) {
        // Selection toggled in adapter
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (llBulkActions != null) {
            if (selectedCount > 0) {
                llBulkActions.setVisibility(View.VISIBLE);
                if (tvSelectedCount != null) {
                    tvSelectedCount.setText(getString(R.string.selection_count, selectedCount));
                }
                View btnShare = getView() != null ? getView().findViewById(R.id.btnBulkShare) : null;
                View btnDelete = getView() != null ? getView().findViewById(R.id.btnBulkDelete) : null;
                View btnRestore = getView() != null ? getView().findViewById(R.id.btnBulkRestore) : null;

                if (selectedTabPosition == 4) {
                    if (btnShare != null) btnShare.setVisibility(View.GONE);
                    if (btnDelete != null) btnDelete.setVisibility(View.GONE);
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
        item.setFavorite(!item.isFavorite());
        repository.updateDocument(item);
        refreshList();
    }

    @Override
    public void onMoreClick(DocumentItem item, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        if (item.isTrash()) {
            popup.getMenu().add(0, 1, 0, getString(R.string.menu_restore));
            popup.getMenu().add(0, 2, 0, getString(R.string.menu_delete_perm));
        } else {
            popup.getMenu().add(0, 3, 0, getString(R.string.menu_open));
            popup.getMenu().add(0, 4, 0, getString(R.string.menu_share));
            popup.getMenu().add(0, 5, 0, getString(R.string.menu_rename));
            popup.getMenu().add(0, 6, 0, getString(R.string.menu_move_trash));
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();
            if (itemId == 1) {
                item.setTrash(false);
                repository.updateDocument(item);
                refreshList();
                BrandedToast.show(requireContext(), getString(R.string.msg_doc_restored));
                return true;
            } else if (itemId == 2) {
                confirmPermanentDelete(item);
                return true;
            } else if (itemId == 3) {
                openDocument(item);
                return true;
            } else if (itemId == 4) {
                shareDocument(item);
                return true;
            } else if (itemId == 5) {
                renameDocumentDialog(item);
                return true;
            } else if (itemId == 6) {
                confirmMoveToTrash(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void openDocument(DocumentItem item) {
        File file = new File(item.getPath());
        if (!file.exists() || file.length() == 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.file_not_found_title)
                    .setMessage(getString(R.string.file_not_found_msg, item.getName()))
                    .setIcon(R.drawable.asterine_logo_image)
                    .setPositiveButton(R.string.btn_delete_from_app, (dialog, which) -> {
                        if (repository != null) {
                            repository.deletePermanently(item.getId());
                            refreshList();
                        }
                        BrandedToast.show(requireContext(), getString(R.string.msg_document_deleted));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
            Intent intent = new Intent(requireContext(), ExcelViewerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            startActivity(intent);
            return;
        }

        if (name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp") || name.endsWith(".ppsx")) {
            BrandedToast.show(requireContext(), "PowerPoint files are currently not supported by Asterine.");
            return;
        }

        if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") ||
            name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")) {
            BrandedToast.show(requireContext(), getString(R.string.msg_prep_doc_view));
            final File targetFile = file;
            Executors.newSingleThreadExecutor().execute(() -> {
                File viewableFile = targetFile;
                try {
                    if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt")) {
                        viewableFile = PdfProcessor.wordToPdf(requireContext(), targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                    } else {
                        viewableFile = PdfProcessor.textToPdf(requireContext(), targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                    }
                } catch (Exception ignored) {}

                final File finalViewable = viewableFile;
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(requireContext(), PdfViewerActivity.class);
                        intent.putExtra("FILE_PATH", finalViewable.getAbsolutePath());
                        startActivity(intent);
                    });
                }
            });
        } else {
            Intent intent = new Intent(requireContext(), PdfViewerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            startActivity(intent);
        }
    }

    private void shareDocument(DocumentItem item) {
        File file = new File(item.getPath());
        if (!file.exists() || file.length() == 0) {
            file = SamplePdfGenerator.createSamplePdf(requireContext(), item.getName(), item.getName());
            item.setPath(file.getAbsolutePath());
            item.setSize(file.length());
            repository.updateDocument(item);
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.menu_share)));
    }

    private String getKey(DocumentItem item) {
        if (item == null) return "";
        return item.getId() != null ? item.getId() : (item.getPath() != null ? item.getPath() : item.getName());
    }

    private void bulkShareSelected() {
        Set<String> selectedKeys = adapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        ArrayList<Uri> uris = new ArrayList<>();
        List<DocumentItem> all = adapter.getItems();
        for (DocumentItem item : all) {
            String key = getKey(item);
            if (selectedKeys.contains(key)) {
                File file = new File(item.getPath());
                if (!file.exists() || file.length() == 0) {
                    file = SamplePdfGenerator.createSamplePdf(requireContext(), item.getName(), item.getName());
                    item.setPath(file.getAbsolutePath());
                    item.setSize(file.length());
                    repository.updateDocument(item);
                }
                Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
                uris.add(uri);
            }
        }

        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.menu_share)));
        }
    }

    private void bulkRestoreSelected() {
        Set<String> selectedKeys = adapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        Set<String> keysToProcess = new HashSet<>(selectedKeys);
        int count = 0;
        List<DocumentItem> allCopy = new ArrayList<>(adapter.getItems());
        adapter.clearSelection();
        for (DocumentItem item : allCopy) {
            String key = getKey(item);
            if (keysToProcess.contains(key)) {
                item.setTrash(false);
                repository.updateDocument(item);
                count++;
            }
        }
        refreshList();
        BrandedToast.show(requireContext(), getString(R.string.bulk_restore_msg, count));
    }

    private void bulkDeleteSelected() {
        Set<String> selectedKeys = adapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        Set<String> keysToProcess = new HashSet<>(selectedKeys);
        int count = keysToProcess.size();
        boolean isTrashTab = (selectedTabPosition == 4);

        String title = isTrashTab ? getString(R.string.bulk_delete_perm_title) : getString(R.string.bulk_move_trash_title);
        String message = isTrashTab ?
                getString(R.string.bulk_delete_perm_message, count) :
                getString(R.string.bulk_move_trash_message, count);
        String positiveBtn = isTrashTab ? getString(R.string.bulk_delete_perm_positive) : getString(R.string.bulk_move_positive);

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.drawable.asterine_logo_image)
                .setPositiveButton(positiveBtn, (dialog, which) -> {
                    List<DocumentItem> allCopy = new ArrayList<>(adapter.getItems());
                    adapter.clearSelection();
                    for (DocumentItem item : allCopy) {
                        String key = getKey(item);
                        if (keysToProcess.contains(key)) {
                            if (isTrashTab || item.isTrash()) {
                                repository.deletePermanently(item.getId());
                            } else {
                                repository.moveToTrash(item.getId());
                            }
                        }
                    }
                    refreshList();
                    String toastMsg = isTrashTab ?
                            getString(R.string.bulk_perm_deleted_msg, count) :
                            getString(R.string.bulk_trash_msg, count);
                    BrandedToast.show(requireContext(), toastMsg);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }



    private void renameDocumentDialog(DocumentItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.rename_document);
        builder.setIcon(R.drawable.asterine_logo_image);

        final EditText input = new EditText(requireContext());
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
                    refreshList();
                }
                BrandedToast.show(requireContext(), getString(R.string.msg_doc_renamed));
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void confirmMoveToTrash(DocumentItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.move_to_trash_title)
                .setMessage(R.string.move_to_trash_msg)
                .setIcon(R.drawable.asterine_logo_image)
                .setPositiveButton(R.string.move_to_trash_btn, (dialog, which) -> {
                    repository.moveToTrash(item.getId());
                    refreshList();
                    BrandedToast.show(requireContext(), getString(R.string.msg_moved_trash));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmPermanentDelete(DocumentItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_permanently_title)
                .setMessage(R.string.delete_permanently_msg)
                .setIcon(R.drawable.asterine_logo_image)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.deletePermanently(item.getId());
                    refreshList();
                    BrandedToast.show(requireContext(), getString(R.string.msg_deleted_perm));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

