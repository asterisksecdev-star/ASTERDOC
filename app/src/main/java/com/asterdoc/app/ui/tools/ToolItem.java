package com.asterdoc.app.ui.tools;

public class ToolItem {
    private String id;
    private String title;
    private int iconResId;
    private String toolType;

    public ToolItem(String id, String title, int iconResId, String toolType) {
        this.id = id;
        this.title = title;
        this.iconResId = iconResId;
        this.toolType = toolType;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getIconResId() { return iconResId; }
    public String getToolType() { return toolType; }
}
