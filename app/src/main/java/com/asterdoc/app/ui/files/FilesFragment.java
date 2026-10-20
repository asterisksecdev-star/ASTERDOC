package com.asterdoc.app.ui.files;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

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
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class FilesFragment extends Fragment implements DocumentAdapter.OnDocumentClickListener {

    private DocumentRepository repository;
    private DocumentAdapter adapter;
    private RecyclerView rvFilesList;
    private TextView tvEmptyFiles;
    private View llTrashActions;
    private View llBulkActions;
    private TextView tvSelectedCount;
    private int selectedTabPosition = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        repository = DocumentRepository.getInstance(requireContext());

        rvFilesList = view.findViewById(R.id.rvFilesList);
        tvEmptyFiles = view.findViewById(R.id.tvEmptyFiles);
        llTrashActions = view.findViewById(R.id.llTrashActions);
        llBulkActions = view.findViewById(R.id.llBulkActions);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        View btnEmptyTrash = view.findViewById(R.id.btnEmptyTrash);

        View btnBulkSelectAll = view.findViewById(R.id.btnBulkSelectAll);
        View btnBulkRestore = view.findViewById(R.id.btnBulkRestore);
        View btnBulkShare = view.findViewById(R.id.btnBulkShare);
        View btnBulkCompress = view.findViewById(R.id.btnBulkCompress);
        View btnBulkDelete = view.findViewById(R.id.btnBulkDelete);
        View btnBulkClose = view.findViewById(R.id.btnBulkClose);

        if (btnBulkSelectAll != null) btnBulkSelectAll.setOnClickListener(v -> adapter.toggleSelectAll());
        if (btnBulkRestore != null) btnBulkRestore.setOnClickListener(v -> bulkRestoreSelected());
        if (btnBulkShare != null) btnBulkShare.setOnClickListener(v -> bulkShareSelected());
        if (btnBulkCompress != null) btnBulkCompress.setOnClickListener(v -> bulkCompressSelected());
        if (btnBulkDelete != null) btnBulkDelete.setOnClickListener(v -> bulkDeleteSelected());
        if (btnBulkClose != null) btnBulkClose.setOnClickListener(v -> adapter.clearSelection());

        if (btnEmptyTrash != null) {
            btnEmptyTrash.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.empty_trash_title)
                        .setMessage(R.string.empty_trash_msg)
                        .setIcon(R.mipmap.ic_launcher)
                        .setPositiveButton(R.string.empty_trash, (dialog, which) -> {
                            repository.emptyTrash();
                            refreshList();
                            BrandedToast.show(requireContext(), getString(R.string.msg_trash_emptied));
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }

        TabLayout tabLayout = view.findViewById(R.id.tabLayoutFiles);

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.all)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.created)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.scanned)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.favorites)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.trash)));

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

    private List<DocumentItem> getFilteredList() {
        switch (selectedTabPosition) {
            case 1:
                return repository.getCreatedDocuments();
            case 2:
                return repository.getScannedDocuments();
            case 3:
                return repository.getFavoriteDocuments();
            case 4:
                return repository.getTrashDocuments();
            case 0:
            default:
                return repository.getAllDocuments();
        }
    }

    private void refreshList() {
        if (llTrashActions != null) {
            llTrashActions.setVisibility(selectedTabPosition == 4 ? View.VISIBLE : View.GONE);
        }
        if (adapter != null) {
            adapter.clearSelection();
        }
        List<DocumentItem> list = getFilteredList();
        updateUI(list);
    }

    private void updateUI(List<DocumentItem> list) {
        adapter.updateData(list);
        if (list.isEmpty()) {
            switch (selectedTabPosition) {
                case 3:
                    tvEmptyFiles.setText(getString(R.string.no_favorite_files_found));
                    break;
                case 4:
                    tvEmptyFiles.setText(getString(R.string.no_trash_files_found));
                    break;
                default:
                    tvEmptyFiles.setText(getString(R.string.no_files_found_in_section));
                    break;
            }
            tvEmptyFiles.setVisibility(View.VISIBLE);
            rvFilesList.setVisibility(View.GONE);
        } else {
            tvEmptyFiles.setVisibility(View.GONE);
            rvFilesList.setVisibility(View.VISIBLE);
            rvFilesList.scheduleLayoutAnimation();
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
                View btnCompress = getView() != null ? getView().findViewById(R.id.btnBulkCompress) : null;
                View btnDelete = getView() != null ? getView().findViewById(R.id.btnBulkDelete) : null;
                View btnRestore = getView() != null ? getView().findViewById(R.id.btnBulkRestore) : null;

                if (selectedTabPosition == 4) {
                    if (btnShare != null) btnShare.setVisibility(View.GONE);
                    if (btnCompress != null) btnCompress.setVisibility(View.GONE);
                    if (btnDelete != null) btnDelete.setVisibility(View.GONE);
                    if (btnRestore != null) btnRestore.setVisibility(View.VISIBLE);
                } else {
                    if (btnShare != null) btnShare.setVisibility(View.VISIBLE);
                    if (btnCompress != null) btnCompress.setVisibility(View.VISIBLE);
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
                repository.moveToTrash(item.getId());
                refreshList();
                BrandedToast.show(requireContext(), getString(R.string.msg_moved_trash));
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
            repository.updateDocument(item);
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

        int count = 0;
        List<DocumentItem> all = adapter.getItems();
        for (DocumentItem item : all) {
            String key = getKey(item);
            if (selectedKeys.contains(key)) {
                item.setTrash(false);
                repository.updateDocument(item);
                count++;
            }
        }
        adapter.clearSelection();
        refreshList();
        BrandedToast.show(requireContext(), getString(R.string.bulk_restore_msg, count));
    }

    private void bulkDeleteSelected() {
        Set<String> selectedKeys = adapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        int count = selectedKeys.size();
        boolean isTrashTab = (selectedTabPosition == 4);

        String title = isTrashTab ? getString(R.string.bulk_delete_perm_title) : getString(R.string.bulk_move_trash_title);
        String message = isTrashTab ?
                getString(R.string.bulk_delete_perm_message, count) :
                getString(R.string.bulk_move_trash_message, count);
        String positiveBtn = isTrashTab ? getString(R.string.bulk_delete_perm_positive) : getString(R.string.bulk_move_positive);

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setIcon(R.mipmap.ic_launcher)
                .setPositiveButton(positiveBtn, (dialog, which) -> {
                    List<DocumentItem> all = adapter.getItems();
                    for (DocumentItem item : all) {
                        String key = getKey(item);
                        if (selectedKeys.contains(key)) {
                            if (isTrashTab || item.isTrash()) {
                                repository.deletePermanently(item.getId());
                            } else {
                                repository.moveToTrash(item.getId());
                            }
                        }
                    }
                    adapter.clearSelection();
                    refreshList();
                    String toastMsg = isTrashTab ?
                            getString(R.string.bulk_perm_deleted_msg, count) :
                            getString(R.string.bulk_trash_msg, count);
                    BrandedToast.show(requireContext(), toastMsg);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void bulkCompressSelected() {
        Set<String> selectedKeys = adapter.getSelectedIds();
        if (selectedKeys.isEmpty()) return;

        List<DocumentItem> all = adapter.getItems();
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
                        repository.addDocument(newItem);
                    }
                } catch (Exception ignored) {}
            }

            final int count = successCount;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.clearSelection();
                    refreshList();
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
                repository.updateDocument(item);
                refreshList();
                BrandedToast.show(requireContext(), getString(R.string.msg_doc_renamed));
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void confirmPermanentDelete(DocumentItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_permanently_title)
                .setMessage(R.string.delete_permanently_msg)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.deletePermanently(item.getId());
                    refreshList();
                    BrandedToast.show(requireContext(), getString(R.string.msg_deleted_perm));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
