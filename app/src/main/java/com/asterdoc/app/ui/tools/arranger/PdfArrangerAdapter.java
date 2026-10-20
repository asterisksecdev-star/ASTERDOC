package com.asterdoc.app.ui.tools.arranger;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;

import java.util.Collections;
import java.util.List;

public class PdfArrangerAdapter extends RecyclerView.Adapter<PdfArrangerAdapter.ViewHolder> {

    public interface OnItemInteractionListener {
        void onItemRemoved(int position);
        void onItemMoved(int fromPosition, int toPosition);
    }

    private final List<ArrangerPageItem> items;
    private final OnItemInteractionListener listener;
    private ItemTouchHelper touchHelper;

    public PdfArrangerAdapter(List<ArrangerPageItem> items, OnItemInteractionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setTouchHelper(ItemTouchHelper touchHelper) {
        this.touchHelper = touchHelper;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_arranger_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ArrangerPageItem item = items.get(position);
        holder.tvPageTitle.setText("Page " + (item.getPageIndex() + 1) + " (" + item.getDocName() + ")");
        holder.tvPageSubtitle.setText("Position: " + (position + 1));
        if (item.getThumbnail() != null) {
            holder.ivPageThumbnail.setImageBitmap(item.getThumbnail());
        }

        holder.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                touchHelper.startDrag(holder);
            }
            return false;
        });

        holder.btnRemovePage.setOnClickListener(v -> {
            int pos = holder.getAbsoluteAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                items.remove(pos);
                notifyItemRemoved(pos);
                listener.onItemRemoved(pos);
                updatePositions();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        updatePositions();
        listener.onItemMoved(fromPosition, toPosition);
    }

    private void updatePositions() {
        notifyItemRangeChanged(0, items.size());
    }

    public List<ArrangerPageItem> getItems() {
        return items;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDragHandle;
        ImageView ivPageThumbnail;
        TextView tvPageTitle;
        TextView tvPageSubtitle;
        ImageButton btnRemovePage;

        ViewHolder(View itemView) {
            super(itemView);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
            ivPageThumbnail = itemView.findViewById(R.id.ivPageThumbnail);
            tvPageTitle = itemView.findViewById(R.id.tvPageTitle);
            tvPageSubtitle = itemView.findViewById(R.id.tvPageSubtitle);
            btnRemovePage = itemView.findViewById(R.id.btnRemovePage);
        }
    }
}
