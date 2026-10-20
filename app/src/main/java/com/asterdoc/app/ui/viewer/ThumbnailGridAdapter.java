package com.asterdoc.app.ui.viewer;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.google.android.material.card.MaterialCardView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailGridAdapter extends RecyclerView.Adapter<ThumbnailGridAdapter.ThumbnailViewHolder> {

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int position);
    }

    private final PdfRenderer renderer;
    private final int pageCount;
    private final int selectedPosition;
    private final OnThumbnailClickListener listener;
    private final LruCache<Integer, Bitmap> cache;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ThumbnailGridAdapter(PdfRenderer renderer, int selectedPosition, OnThumbnailClickListener listener) {
        this.renderer = renderer;
        this.pageCount = renderer != null ? renderer.getPageCount() : 0;
        this.selectedPosition = selectedPosition;
        this.listener = listener;

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        this.cache = new LruCache<Integer, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public ThumbnailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumbnail_page, parent, false);
        return new ThumbnailViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbnailViewHolder holder, int position) {
        holder.tvThumbnailPageNum.setText(String.valueOf(position + 1));

        if (position == selectedPosition) {
            holder.cardThumbnailContainer.setStrokeColor(0xFF00897B); // Primary accent border
            holder.cardThumbnailContainer.setStrokeWidth(6);
        } else {
            holder.cardThumbnailContainer.setStrokeColor(0xFF3C4043);
            holder.cardThumbnailContainer.setStrokeWidth(2);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onThumbnailClick(position);
            }
        });

        Bitmap cached = cache.get(position);
        if (cached != null && !cached.isRecycled()) {
            holder.ivThumbnailPage.setImageBitmap(cached);
            return;
        }

        holder.ivThumbnailPage.setImageBitmap(null);

        executor.execute(() -> {
            if (renderer == null) return;
            try {
                Bitmap bitmap;
                synchronized (renderer) {
                    PdfRenderer.Page page = renderer.openPage(position);
                    int width = 300;
                    int height = Math.max(1, (page.getHeight() * width) / page.getWidth());
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                }

                if (bitmap != null) {
                    cache.put(position, bitmap);
                    mainHandler.post(() -> {
                        if (holder.getAdapterPosition() == position) {
                            holder.ivThumbnailPage.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    public void cleanup() {
        executor.shutdownNow();
        cache.evictAll();
    }

    static class ThumbnailViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardThumbnailContainer;
        ImageView ivThumbnailPage;
        TextView tvThumbnailPageNum;

        ThumbnailViewHolder(@NonNull View itemView) {
            super(itemView);
            cardThumbnailContainer = itemView.findViewById(R.id.cardThumbnailContainer);
            ivThumbnailPage = itemView.findViewById(R.id.ivThumbnailPage);
            tvThumbnailPageNum = itemView.findViewById(R.id.tvThumbnailPageNum);
        }
    }
}
