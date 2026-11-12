package com.asterdoc.app.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.ads.AdManager;
import com.asterdoc.app.data.DocumentItem;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DocumentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnDocumentClickListener {
        void onDocumentClick(DocumentItem item);
        void onDocumentLongClick(DocumentItem item);
        void onFavoriteClick(DocumentItem item);
        void onMoreClick(DocumentItem item, View anchor);
        void onSelectionChanged(int selectedCount);
    }

    private static final int TYPE_FILE = 0;
    private static final int TYPE_NATIVE_AD = 1;
    private static final int AD_INSERTION_POSITION = 3;

    private List<DocumentItem> items;
    private final OnDocumentClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private boolean isSelectionMode = false;
    private boolean isTrashMode = false;
    private final Set<String> selectedKeys = new HashSet<>();

    public void setTrashMode(boolean trashMode) {
        this.isTrashMode = trashMode;
        notifyDataSetChanged();
    }

    public DocumentAdapter(List<DocumentItem> items, OnDocumentClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    private String getKey(DocumentItem item) {
        if (item == null) return "";
        return item.getId() != null ? item.getId() : (item.getPath() != null ? item.getPath() : item.getName());
    }

    private int getItemIndex(int position) {
        if (items != null && items.size() >= AD_INSERTION_POSITION && position > AD_INSERTION_POSITION) {
            return position - 1;
        }
        return position;
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

        notifyDataSetChanged();

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

    @Override
    public int getItemViewType(int position) {
        if (!isTrashMode && items != null && items.size() >= AD_INSERTION_POSITION && position == AD_INSERTION_POSITION) {
            return TYPE_NATIVE_AD;
        }
        return TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_NATIVE_AD) {
            FrameLayout frameLayout = new FrameLayout(parent.getContext());
            frameLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new AdViewHolder(frameLayout);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document, parent, false);
            return new ViewHolder(view);
        }
    }

    public static String getRelativeTime(long timeMillis) {
        long now = System.currentTimeMillis();
        long diff = now - timeMillis;
        if (diff < 60 * 1000) return "Just now";
        long minutes = diff / (60 * 1000);
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 30) return days + (days == 1 ? " day ago" : " days ago");
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        return sdf.format(new Date(timeMillis));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (getItemViewType(position) == TYPE_NATIVE_AD) {
            AdViewHolder adHolder = (AdViewHolder) rawHolder;
            ViewGroup container = (ViewGroup) adHolder.itemView;
            AdManager.loadNativeAd(container.getContext(), container);
            return;
        }

        ViewHolder holder = (ViewHolder) rawHolder;
        int itemIndex = getItemIndex(position);
        if (items == null || itemIndex < 0 || itemIndex >= items.size()) return;

        DocumentItem item = items.get(itemIndex);
        holder.tvName.setText(item.getName());
        String details = getRelativeTime(item.getDate()) + "  " + item.getFormattedSize();
        holder.tvDetails.setText(details);

        String type = item.getType();
        String name = item.getName().toLowerCase();
        int iconRes;
        if (type != null && type.equalsIgnoreCase("WORD") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") || name.endsWith(".dot")) {
            iconRes = R.drawable.ic_cat_word;
        } else if (type != null && (type.equalsIgnoreCase("EXCEL") || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv"))) {
            iconRes = R.drawable.ic_cat_excel;
        } else if (type != null && type.equalsIgnoreCase("TXT") || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) {
            iconRes = R.drawable.ic_cat_txt;
        } else {
            iconRes = R.drawable.ic_cat_pdf;
        }
        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setBackground(null);
        holder.ivIcon.setImageTintList(null);
        holder.ivIcon.setBackgroundTintList(null);

        String key = getKey(item);
        boolean isSelected = selectedKeys.contains(key);
        if (holder.cardView != null) {
            if (isSelected) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#1B2A22"));
                holder.cardView.setStrokeWidth(3);
                holder.cardView.setStrokeColor(Color.parseColor("#107C41"));
            } else {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#16171B"));
                holder.cardView.setStrokeWidth(1);
                holder.cardView.setStrokeColor(Color.parseColor("#2E323D"));
            }
        }

        if (isSelectionMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(isSelected);
            holder.btnFavorite.setVisibility(View.GONE);
            holder.btnMore.setVisibility(View.VISIBLE);
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            if (isTrashMode) {
                holder.btnFavorite.setVisibility(View.GONE);
            } else {
                holder.btnFavorite.setVisibility(View.VISIBLE);
                if (item.isFavorite()) {
                    holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
                    holder.btnFavorite.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFB300")));
                } else {
                    holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
                    holder.btnFavorite.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                }
            }
            holder.btnMore.setVisibility(View.VISIBLE);
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
        if (items == null || items.isEmpty()) return 0;
        if (!isTrashMode && items.size() >= AD_INSERTION_POSITION) {
            return items.size() + 1;
        }
        return items.size();
    }

    static class AdViewHolder extends RecyclerView.ViewHolder {
        AdViewHolder(View itemView) {
            super(itemView);
        }
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

