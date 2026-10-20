package com.asterdoc.app.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {

    public interface OnPageClickListener {
        void onPageClick();
    }

    private ParcelFileDescriptor pfd;
    private final PdfRenderer renderer;
    private int pageCount = 0;
    private final LruCache<Integer, Bitmap> memoryCache;
    private final OnPageClickListener clickListener;

    private final ExecutorService renderExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DrawingCanvasView.ToolMode activeToolMode = DrawingCanvasView.ToolMode.NONE;
    private int activeColor = Color.RED;
    private float activeStrokeWidth = 8f;
    private String activeSearchQuery = null;

    public void setSearchQuery(String query) {
        this.activeSearchQuery = query;
        notifyDataSetChanged();
    }

    public PdfPageAdapter(File pdfFile, OnPageClickListener clickListener) {
        this.clickListener = clickListener;
        PdfRenderer tempRenderer = null;
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            tempRenderer = new PdfRenderer(pfd);
            pageCount = tempRenderer.getPageCount();
        } catch (Exception e) {
            pageCount = 0;
        }
        this.renderer = tempRenderer;

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 3;
        memoryCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public PdfRenderer getRenderer() {
        return renderer;
    }

    public void setDrawingTool(DrawingCanvasView.ToolMode mode, int color, float strokeWidth) {
        this.activeToolMode = mode;
        this.activeColor = color;
        this.activeStrokeWidth = strokeWidth;
        notifyDataSetChanged();
    }

    public DrawingCanvasView.ToolMode getActiveToolMode() {
        return activeToolMode;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.drawingCanvas.setToolMode(activeToolMode);
        holder.drawingCanvas.setColor(activeColor);
        holder.drawingCanvas.setStrokeWidth(activeStrokeWidth);
        holder.drawingCanvas.setSearchQuery(activeSearchQuery);

        holder.ivPdfPage.setOnMatrixChangedListener((matrix, scale) -> holder.drawingCanvas.setDrawMatrix(matrix));

        if (renderer != null && position < pageCount) {
            holder.ivPdfPage.setOnZoomImageClickListener(() -> {
                if (clickListener != null && activeToolMode == DrawingCanvasView.ToolMode.NONE) {
                    clickListener.onPageClick();
                }
            });

            Bitmap cachedBitmap = memoryCache.get(position);
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                holder.ivPdfPage.setImageBitmap(cachedBitmap);
                return;
            }

            // Clear old image while loading new page
            holder.ivPdfPage.setImageBitmap(null);

            final int adapterPosition = position;
            renderExecutor.execute(() -> {
                if (renderer == null) return;
                try {
                    Bitmap bitmap;
                    synchronized (renderer) {
                        PdfRenderer.Page page = renderer.openPage(adapterPosition);
                        int screenWidth = holder.itemView.getContext().getResources().getDisplayMetrics().widthPixels;
                        float densityScale = 3.5f; // Ultra high definition crisp text resolution
                        int width = Math.max(1, (int) (screenWidth * densityScale));
                        int height = Math.max(1, (page.getHeight() * width) / page.getWidth());

                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();
                    }

                    if (bitmap != null) {
                        memoryCache.put(adapterPosition, bitmap);
                        mainHandler.post(() -> {
                            if (holder.getAdapterPosition() == adapterPosition) {
                                holder.ivPdfPage.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    public void closeRenderer() {
        try {
            renderExecutor.shutdownNow();
            if (renderer != null) {
                synchronized (renderer) {
                    renderer.close();
                }
            }
            if (pfd != null) pfd.close();
            memoryCache.evictAll();
        } catch (Exception ignored) {}
    }

    public static class PageViewHolder extends RecyclerView.ViewHolder {
        public ZoomImageView ivPdfPage;
        public DrawingCanvasView drawingCanvas;

        PageViewHolder(View itemView) {
            super(itemView);
            ivPdfPage = itemView.findViewById(R.id.ivPdfPage);
            drawingCanvas = itemView.findViewById(R.id.drawingCanvas);
        }
    }
}
