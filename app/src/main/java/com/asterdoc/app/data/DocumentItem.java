package com.asterdoc.app.data;

import java.io.Serializable;

public class DocumentItem implements Serializable {
    private String id;
    private String name;
    private String path;
    private String type; // "PDF", "SCAN", "IMAGE", "WORD"
    private long size;
    private long date;
    private boolean isFavorite;
    private boolean isTrash;
    private String category; // "Recent", "Created", "Scanned"

    public DocumentItem(String id, String name, String path, String type, long size, long date, String category) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.date = date;
        this.category = category;
        this.isFavorite = false;
        this.isTrash = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public boolean isTrash() { return isTrash; }
    public void setTrash(boolean trash) { isTrash = trash; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }
}
