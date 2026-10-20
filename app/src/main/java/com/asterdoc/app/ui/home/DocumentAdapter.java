package com.asterdoc.app.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.ViewHolder> {

    public interface OnDocumentClickListener {
        void onDocumentClick(DocumentItem item);
        void onDocumentLongClick(DocumentItem item);
        void onFavoriteClick(DocumentItem item);
        void onMoreClick(DocumentItem item, View anchor);
        void onSelectionChanged(int selectedCount);
    }

    private List<DocumentItem> items;
    private final OnDocumentClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private boolean isSelectionMode = false;
    private final Set<String> selectedKeys = new HashSet<>();

    public DocumentAdapter(List<DocumentItem> items, OnDocumentClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    private String getKey(DocumentItem item) {
        if (item == null) return "";
        return item.getId() != null ? item.getId() : (item.getPath() != null ? item.getPath() : item.getName());
    }

    public void updateData(List<DocumentItem> newItems) {
        this.items = newItems;
        Set<String> validKeys = new HashSet<>();
        for (DocumentItem item : newItems) {
            validKeys.add(getKey(item));
        }
        selectedKeys.retainAll(validKeys);
        if (selectedKeys.isEmpty() && isSelectionMode) {
            isSelectionMode = false;
        }
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) {
            selectedKeys.clear();
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedKeys.size());
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(DocumentItem item) {
        String key = getKey(item);
        boolean wasSelectionMode = isSelectionMode;
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
            if (selectedKeys.isEmpty()) {
                isSelectionMode = false;
            }
        } else {
            selectedKeys.add(key);
            isSelectionMode = true;
        }

        int position = -1;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (getKey(items.get(i)).equals(key)) {
                    position = i;
                    break;
                }
            }
        }

        if (wasSelectionMode != isSelectionMode || position == -1) {
            notifyDataSetChanged();
        } else {
            notifyItemChanged(position);
        }

        if (listener != null) {
            listener.onSelectionChanged(selectedKeys.size());
        }
    }

    public void selectAll() {
        selectedKeys.clear();
        for (DocumentItem item : items) {
            selectedKeys.add(getKey(item));
        }
        isSelectionMode = true;
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedKeys.size());
        }
    }

    public void toggleSelectAll() {
        if (items != null && !items.isEmpty() && selectedKeys.size() == items.size()) {
            clearSelection();
        } else {
            selectAll();
        }
    }

    public void clearSelection() {
        selectedKeys.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedKeys.size());
        }
    }

    public Set<String> getSelectedIds() {
        return selectedKeys;
    }

    public List<DocumentItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentItem item = items.get(position);
        holder.tvName.setText(item.getName());
        String details = item.getType() + " • " + item.getFormattedSize() + " • " + dateFormat.format(new Date(item.getDate()));
        holder.tvDetails.setText(details);

        String type = item.getType();
        String name = item.getName().toLowerCase();
        int iconRes;
        int badgeColor;
        if (type != null && type.equalsIgnoreCase("WORD") || name.endsWith(".docx") || name.endsWith(".doc")) {
            iconRes = android.R.drawable.ic_menu_edit;
            badgeColor = Color.parseColor("#1A73E8"); // Blue for Word
        } else if (type != null && (type.equalsIgnoreCase("EXCEL") || name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            iconRes = android.R.drawable.ic_menu_sort_by_size;
            badgeColor = Color.parseColor("#0D9488"); // Teal for Excel
        } else if (type != null && type.equalsIgnoreCase("TXT") || name.endsWith(".txt")) {
            iconRes = android.R.drawable.ic_menu_search;
            badgeColor = Color.parseColor("#D97706"); // Amber for TXT
        } else {
            iconRes = android.R.drawable.ic_menu_save;
            badgeColor = Color.parseColor("#EA4335"); // Red for PDF
        }
        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setBackgroundResource(R.drawable.bg_tool_icon_container);
        holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(badgeColor));

        String key = getKey(item);
        boolean isSelected = selectedKeys.contains(key);
        if (holder.cardView != null) {
            if (isSelected) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#E2F2E9"));
                holder.cardView.setStrokeWidth(3);
                holder.cardView.setStrokeColor(Color.parseColor("#107C41"));
            } else {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
                holder.cardView.setStrokeWidth(0);
            }
        }

        if (isSelectionMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(isSelected);
            holder.btnFavorite.setVisibility(View.GONE);
            holder.btnMore.setVisibility(View.GONE);
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            holder.btnFavorite.setVisibility(View.VISIBLE);
            holder.btnMore.setVisibility(View.VISIBLE);
            if (item.isFavorite()) {
                holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item);
            } else {
                listener.onDocumentClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(item);
                if (listener != null) {
                    listener.onDocumentLongClick(item);
                }
                return true;
            } else {
                toggleSelection(item);
                return true;
            }
        });

        holder.cbSelect.setOnClickListener(v -> toggleSelection(item));

        holder.btnFavorite.setOnClickListener(v -> listener.onFavoriteClick(item));
        holder.btnMore.setOnClickListener(v -> listener.onMoreClick(item, v));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        CheckBox cbSelect;
        ImageView ivIcon;
        TextView tvName;
        TextView tvDetails;
        ImageButton btnFavorite;
        ImageButton btnMore;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            cbSelect = itemView.findViewById(R.id.cbSelect);
            ivIcon = itemView.findViewById(R.id.ivDocIcon);
            tvName = itemView.findViewById(R.id.tvDocName);
            tvDetails = itemView.findViewById(R.id.tvDocDetails);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnMore = itemView.findViewById(R.id.btnMore);
        }
    }
}
