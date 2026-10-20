package com.asterdoc.app.ui.tools;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;

import java.util.List;

public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ViewHolder> {

    public interface OnToolClickListener {
        void onToolClick(ToolItem tool);
    }

    private final List<ToolItem> tools;
    private final OnToolClickListener listener;

    public ToolAdapter(List<ToolItem> tools, OnToolClickListener listener) {
        this.tools = tools;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ToolItem tool = tools.get(position);
        holder.tvTitle.setText(tool.getTitle());
        holder.ivIcon.setImageResource(tool.getIconResId());
        holder.ivIcon.setBackgroundResource(R.drawable.bg_tool_icon_container);
        holder.ivIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));

        int tintColor;
        String type = tool.getToolType();
        if (type == null) type = "";
        switch (type) {
            case "WORD_PDF":
                tintColor = android.graphics.Color.parseColor("#EA4335"); // Red for PDF
                break;
            case "PDF_WORD":
                tintColor = android.graphics.Color.parseColor("#1A73E8"); // Blue for Word
                break;
            case "IMAGES_PDF":
                tintColor = android.graphics.Color.parseColor("#34A853"); // Green
                break;
            case "EXCEL_PDF":
            case "PDF_EXCEL":
                tintColor = android.graphics.Color.parseColor("#0D9488"); // Teal for Excel
                break;
            case "MERGE":
                tintColor = android.graphics.Color.parseColor("#9333EA"); // Purple
                break;
            case "SPLIT":
            case "COMPRESS":
                tintColor = android.graphics.Color.parseColor("#F97316"); // Orange
                break;
            case "ARRANGE":
            case "SIGN":
                tintColor = android.graphics.Color.parseColor("#4F46E5"); // Indigo
                break;
            case "SCAN":
                tintColor = android.graphics.Color.parseColor("#0284C7"); // Cyan
                break;
            case "OCR":
                tintColor = android.graphics.Color.parseColor("#D97706"); // Amber
                break;
            default:
                tintColor = android.graphics.Color.parseColor("#1A73E8"); // Primary Blue
                break;
        }
        holder.ivIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tintColor));

        holder.itemView.setOnClickListener(v -> listener.onToolClick(tool));
    }

    @Override
    public int getItemCount() {
        return tools.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivToolIcon);
            tvTitle = itemView.findViewById(R.id.tvToolTitle);
        }
    }
}
