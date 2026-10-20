package com.asterdoc.app.ui.tools.arranger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class PdfArrangerActivity extends AppCompatActivity implements PdfArrangerAdapter.OnItemInteractionListener {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    private final List<ArrangerPageItem> arrangerItems = new ArrayList<>();
    private PdfArrangerAdapter adapter;
    private RecyclerView rvArrangerPages;
    private ItemTouchHelper touchHelper;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            uris.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        uris.add(data.getData());
                    }
                    loadPagesFromUris(uris);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pdf_arranger);

        MaterialToolbar toolbar = findViewById(R.id.toolbarArranger);
        toolbar.setTitle(R.string.arranger_title);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvArrangerPages = findViewById(R.id.rvArrangerPages);
        rvArrangerPages.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PdfArrangerAdapter(arrangerItems, this);
        rvArrangerPages.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAbsoluteAdapterPosition();
                int to = target.getAbsoluteAdapterPosition();
                adapter.onItemMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        touchHelper.attachToRecyclerView(rvArrangerPages);

        adapter.setTouchHelper(touchHelper);

        MaterialButton btnAddMorePdf = findViewById(R.id.btnAddMorePdf);
        MaterialButton btnSaveArrangedPdf = findViewById(R.id.btnSaveArrangedPdf);

        btnAddMorePdf.setOnClickListener(v -> openFilePicker());
        btnSaveArrangedPdf.setOnClickListener(v -> saveArrangedPdf());

        openFilePicker();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    private void loadPagesFromUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        BrandedToast.show(this, getString(R.string.msg_processing_wait));

        Executors.newSingleThreadExecutor().execute(() -> {
            List<ArrangerPageItem> newItems = new ArrayList<>();
            try {
                for (Uri uri : uris) {
                    File tempFile = copyUriToTempFile(uri);
                    if (tempFile != null) {
                        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        PdfRenderer renderer = new PdfRenderer(pfd);
                        int count = renderer.getPageCount();

                        for (int i = 0; i < count; i++) {
                            PdfRenderer.Page page = renderer.openPage(i);
                            Bitmap bitmap = Bitmap.createBitmap(150, 200, Bitmap.Config.ARGB_8888);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                            newItems.add(new ArrangerPageItem(tempFile, i, bitmap, tempFile.getName()));
                            page.close();
                        }
                        renderer.close();
                        pfd.close();
                    }
                }

                runOnUiThread(() -> {
                    arrangerItems.addAll(newItems);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    BrandedToast.show(this, getString(R.string.msg_import_error) + " " + e.getMessage());
                });
            }
        });
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File tempFile = File.createTempFile("arranger_pdf", ".pdf", getCacheDir());
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

    private void saveArrangedPdf() {
        if (arrangerItems.isEmpty()) {
            BrandedToast.show(this, getString(R.string.btn_choose_pdf));
            return;
        }

        BrandedToast.show(this, getString(R.string.msg_processing_wait));

        List<ArrangerPageItem> itemsToSave = new ArrayList<>(arrangerItems);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File outDir = PdfProcessor.getAsterDocsDirectory();
                File outFile = new File(outDir, "Arranged_Document_" + System.currentTimeMillis() + ".pdf");

                PdfDocument document = new PdfDocument();

                for (int index = 0; index < itemsToSave.size(); index++) {
                    ArrangerPageItem item = itemsToSave.get(index);
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(item.getSourceFile(), ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    PdfRenderer.Page page = renderer.openPage(item.getPageIndex());

                    int width = page.getWidth();
                    int height = page.getHeight();

                    Bitmap pageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, height, index + 1).create();
                    PdfDocument.Page newPage = document.startPage(pageInfo);

                    Canvas canvas = newPage.getCanvas();
                    canvas.drawBitmap(pageBitmap, 0, 0, null);

                    document.finishPage(newPage);

                    page.close();
                    renderer.close();
                    pfd.close();
                }

                FileOutputStream fos = new FileOutputStream(outFile);
                document.writeTo(fos);
                document.close();
                fos.close();

                DocumentItem docItem = new DocumentItem(
                        String.valueOf(System.currentTimeMillis()),
                        outFile.getName(),
                        outFile.getAbsolutePath(),
                        "PDF",
                        outFile.length(),
                        System.currentTimeMillis(),
                        "Created"
                );
                DocumentRepository.getInstance(getApplicationContext()).addDocument(docItem);

                runOnUiThread(() -> {
                    BrandedToast.show(this, getString(R.string.msg_processing_complete));

                    Intent intent = new Intent(this, PdfViewerActivity.class);
                    intent.putExtra("FILE_PATH", outFile.getAbsolutePath());
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    BrandedToast.show(this, getString(R.string.msg_import_error) + " " + e.getMessage());
                });
            }
        });
    }

    @Override
    public void onItemRemoved(int position) {
        // Handled by adapter
    }

    @Override
    public void onItemMoved(int fromPosition, int toPosition) {
        // Handled by adapter
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_bottom);
    }
}
