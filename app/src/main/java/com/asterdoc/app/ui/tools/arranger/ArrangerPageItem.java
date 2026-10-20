package com.asterdoc.app.ui.tools.arranger;

import android.graphics.Bitmap;

import java.io.File;

public class ArrangerPageItem {
    private File sourceFile;
    private int pageIndex;
    private Bitmap thumbnail;
    private String docName;

    public ArrangerPageItem(File sourceFile, int pageIndex, Bitmap thumbnail, String docName) {
        this.sourceFile = sourceFile;
        this.pageIndex = pageIndex;
        this.thumbnail = thumbnail;
        this.docName = docName;
    }

    public File getSourceFile() { return sourceFile; }
    public int getPageIndex() { return pageIndex; }
    public Bitmap getThumbnail() { return thumbnail; }
    public String getDocName() { return docName; }
}
