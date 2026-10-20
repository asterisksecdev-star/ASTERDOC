package com.asterdoc.app.ui.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.pdf.SamplePdfGenerator;
import com.asterdoc.app.ui.tools.ToolActionActivity;
import com.asterdoc.app.ui.tools.ToolAdapter;
import com.asterdoc.app.ui.tools.ToolItem;
import com.asterdoc.app.ui.tools.arranger.PdfArrangerActivity;
import com.asterdoc.app.ui.tools.sign.SignPdfActivity;
import com.asterdoc.app.ui.viewer.ExcelViewerActivity;
import com.asterdoc.app.ui.viewer.PdfViewerActivity;
import com.asterdoc.app.util.BrandedToast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment implements DocumentAdapter.OnDocumentClickListener, ToolAdapter.OnToolClickListener {

    private DocumentRepository repository;
    private DocumentAdapter documentAdapter;
    private View llBulkActions;
    private TextView tvSelectedCount;

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

        llBulkActions = view.findViewById(R.id.llBulkActions);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        View btnBulkSelectAll = view.findViewById(R.id.btnBulkSelectAll);
        View btnBulkShare = view.findViewById(R.id.btnBulkShare);
        View btnBulkCompress = view.findViewById(R.id.btnBulkCompress);
        View btnBulkDelete = view.findViewById(R.id.btnBulkDelete);
        View btnBulkClose = view.findViewById(R.id.btnBulkClose);

        if (btnBulkSelectAll != null) btnBulkSelectAll.setOnClickListener(v -> documentAdapter.toggleSelectAll());
        if (btnBulkShare != null) btnBulkShare.setOnClickListener(v -> bulkShareSelected());
        if (btnBulkCompress != null) btnBulkCompress.setOnClickListener(v -> bulkCompressSelected());
        if (btnBulkDelete != null) btnBulkDelete.setOnClickListener(v -> bulkDeleteSelected());
        if (btnBulkClose != null) btnBulkClose.setOnClickListener(v -> documentAdapter.clearSelection());

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

        // Quick Tools Setup
        RecyclerView rvQuickTools = view.findViewById(R.id.rvQuickTools);
        rvQuickTools.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        List<ToolItem> quickTools = getQuickToolsList();
        rvQuickTools.setAdapter(new ToolAdapter(quickTools, this));
        rvQuickTools.setLayoutAnimation(layoutAnim);
        rvQuickTools.scheduleLayoutAnimation();

        // Recent Documents Setup
        RecyclerView rvRecentDocs = view.findViewById(R.id.rvRecentDocs);
        rvRecentDocs.setLayoutManager(new LinearLayoutManager(requireContext()));
        documentAdapter = new DocumentAdapter(repository.getRecentDocuments(), this);
        rvRecentDocs.setAdapter(documentAdapter);
        rvRecentDocs.setLayoutAnimation(layoutAnim);
        rvRecentDocs.scheduleLayoutAnimation();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            RecyclerView rvQuickTools = getView().findViewById(R.id.rvQuickTools);
            RecyclerView rvRecentDocs = getView().findViewById(R.id.rvRecentDocs);
            if (rvQuickTools != null) rvQuickTools.scheduleLayoutAnimation();
            if (rvRecentDocs != null) rvRecentDocs.scheduleLayoutAnimation();
        }
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
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
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
        return result != null ? result : "Document_" + System.currentTimeMillis() + ".pdf";
    }

    private void importAndOpenDocument(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            String fileName = getFileNameFromUri(uri);
            File dir = PdfProcessor.getAsterDocsDirectory();
            File importedFile = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(importedFile);
            byte[] buf = new byte[8192];
            int len;
            while (is != null && (len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            if (is != null) is.close();
            fos.close();

            String type = "PDF";
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
                type = "WORD";
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                type = "EXCEL";
            } else if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) {
                type = "PPT";
            } else if (lower.endsWith(".txt")) {
                type = "TXT";
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
                if (documentAdapter != null) {
                    documentAdapter.updateData(repository.getRecentDocuments());
                }
            }

            openDocument(item);

        } catch (Exception e) {
            BrandedToast.show(requireContext(), getString(R.string.msg_import_error));
        }
    }

    private List<ToolItem> getQuickToolsList() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("1", getString(R.string.scan_document), android.R.drawable.ic_menu_camera, "SCAN"));
        list.add(new ToolItem("2", getString(R.string.merge_pdf), android.R.drawable.ic_menu_add, "MERGE"));
        list.add(new ToolItem("3", getString(R.string.compress_pdf), android.R.drawable.ic_menu_sort_by_size, "COMPRESS"));
        list.add(new ToolItem("4", getString(R.string.sign_pdf), android.R.drawable.ic_menu_send, "SIGN"));
        list.add(new ToolItem("5", getString(R.string.page_arranger), android.R.drawable.ic_menu_sort_alphabetically, "ARRANGE"));
        return list;
    }

    @Override
    public void onDocumentClick(DocumentItem item) {
        openDocument(item);
    }

    @Override
    public void onDocumentLongClick(DocumentItem item) {
        // Handled in adapter
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (llBulkActions != null) {
            if (selectedCount > 0) {
                llBulkActions.setVisibility(View.VISIBLE);
                if (tvSelectedCount != null) {
                    tvSelectedCount.setText(getString(R.string.selection_count, selectedCount));
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
            documentAdapter.updateData(repository.getRecentDocuments());
            BrandedToast.show(requireContext(), getString(item.isFavorite() ? R.string.msg_added_favorites : R.string.msg_removed_favorites));
        }
    }

    @Override
    public void onMoreClick(DocumentItem item, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
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
        popup.show();
    }

    private void openDocument(DocumentItem item) {
        File file = new File(item.getPath());
        if (!file.exists() || file.length() == 0) {
            file = SamplePdfGenerator.createSamplePdf(requireContext(), item.getName(), item.getName());
            item.setPath(file.getAbsolutePath());
            item.setSize(file.length());
            if (repository != null) {
                repository.updateDocument(item);
            }
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) {
            Intent intent = new Intent(requireContext(), ExcelViewerActivity.class);
            intent.putExtra("FILE_PATH", file.getAbsolutePath());
            startActivity(intent);
            return;
        }

        if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") ||
            name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp") ||
            name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".md") || name.endsWith(".html")) {
            BrandedToast.show(requireContext(), getString(R.string.msg_prep_doc_view));
            final File targetFile = file;
            Executors.newSingleThreadExecutor().execute(() -> {
                File viewableFile = targetFile;
                try {
                    if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt")) {
                        viewableFile = PdfProcessor.wordToPdf(requireContext(), targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
                    } else if (name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".pps") || name.endsWith(".odp")) {
                        viewableFile = PdfProcessor.pptxToPdf(requireContext(), targetFile, targetFile.getName().replaceFirst("\\.[^.]+$", "") + "_view.pdf");
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
            if (repository != null) {
                repository.updateDocument(item);
            }
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
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        ArrayList<Uri> uris = new ArrayList<>();
        List<DocumentItem> all = documentAdapter.getItems();
        for (DocumentItem item : all) {
            String key = getKey(item);
            if (selectedKeys.contains(key)) {
                File file = new File(item.getPath());
                if (!file.exists() || file.length() == 0) {
                    file = SamplePdfGenerator.createSamplePdf(requireContext(), item.getName(), item.getName());
                    item.setPath(file.getAbsolutePath());
                    item.setSize(file.length());
                    if (repository != null) repository.updateDocument(item);
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

    private void bulkDeleteSelected() {
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        int count = selectedKeys.size();
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.bulk_move_trash_title))
                .setMessage(getString(R.string.bulk_move_trash_message, count))
                .setIcon(R.mipmap.ic_launcher)
                .setPositiveButton(getString(R.string.bulk_move_positive), (dialog, which) -> {
                    List<DocumentItem> all = documentAdapter.getItems();
                    for (DocumentItem item : all) {
                        String key = getKey(item);
                        if (selectedKeys.contains(key)) {
                            if (repository != null) {
                                repository.moveToTrash(item.getId());
                            }
                        }
                    }
                    if (documentAdapter != null) {
                        documentAdapter.clearSelection();
                        documentAdapter.updateData(repository.getRecentDocuments());
                    }
                    BrandedToast.show(requireContext(), getString(R.string.bulk_trash_msg, count));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void bulkCompressSelected() {
        Set<String> selectedKeys = documentAdapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        List<DocumentItem> all = documentAdapter.getItems();
        List<File> toCompress = new ArrayList<>();
        for (DocumentItem item : all) {
            String key = getKey(item);
            if (selectedKeys.contains(key)) {
                File file = new File(item.getPath());
                if (file.exists()) {
                    toCompress.add(file);
                }
            }
        }

        if (toCompress.isEmpty()) {
            BrandedToast.show(requireContext(), "No valid files to compress");
            return;
        }

        BrandedToast.show(requireContext(), getString(R.string.bulk_compress_toast, toCompress.size()));
        Executors.newSingleThreadExecutor().execute(() -> {
            int successCount = 0;
            for (File file : toCompress) {
                try {
                    File comp = PdfProcessor.compressPdf(requireContext(), file, file.getName());
                    if (comp != null && comp.exists() && comp.length() < file.length()) {
                        successCount++;
                        DocumentItem newItem = new DocumentItem(
                                String.valueOf(System.currentTimeMillis()),
                                comp.getName(),
                                comp.getAbsolutePath(),
                                "PDF",
                                comp.length(),
                                System.currentTimeMillis(),
                                "Created"
                        );
                        if (repository != null) {
                            repository.addDocument(newItem);
                        }
                    }
                } catch (Exception ignored) {}
            }

            final int count = successCount;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (documentAdapter != null && repository != null) {
                        documentAdapter.clearSelection();
                        documentAdapter.updateData(repository.getRecentDocuments());
                    }
                    BrandedToast.show(requireContext(), getString(R.string.bulk_compress_success, count, toCompress.size()));
                });
            }
        });
    }

    private void renameDocumentDialog(DocumentItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.rename_document);
        final EditText input = new EditText(requireContext());
        input.setText(item.getName());
        builder.setView(input);

        builder.setPositiveButton(R.string.rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                item.setName(newName);
                if (repository != null) {
                    repository.updateDocument(item);
                    documentAdapter.updateData(repository.getRecentDocuments());
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
                .setPositiveButton(R.string.move_to_trash_btn, (dialog, which) -> {
                    if (repository != null && documentAdapter != null) {
                        repository.moveToTrash(item.getId());
                        documentAdapter.updateData(repository.getRecentDocuments());
                        BrandedToast.show(requireContext(), getString(R.string.msg_moved_trash));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onToolClick(ToolItem item) {
        String type = item.getToolType();
        Intent intent;
        if ("ARRANGE".equals(type)) {
            intent = new Intent(requireContext(), PdfArrangerActivity.class);
        } else if ("SIGN".equals(type)) {
            intent = new Intent(requireContext(), SignPdfActivity.class);
        } else {
            intent = new Intent(requireContext(), ToolActionActivity.class);
            intent.putExtra("TOOL_TYPE", type);
            intent.putExtra("TOOL_TITLE", item.getTitle());
        }
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out);
        }
    }
}
