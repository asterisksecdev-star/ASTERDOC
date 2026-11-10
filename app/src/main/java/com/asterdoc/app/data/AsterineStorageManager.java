package com.asterdoc.app.data;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class AsterineStorageManager {

    public enum SubFolder {
        PDF("PDF"),
        WORD("Word"),
        EXCEL("Excel"),
        POWERPOINT("PowerPoint"),
        TEXT("Text"),
        IMAGES("Images"),
        CONVERTED("Converted"),
        SIGNED("Signed"),
        MERGED("Merged"),
        ORGANIZED("Organized"),
        SHARED("Shared");

        private final String folderName;

        SubFolder(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    public static File getAsterineDirectory(Context context) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Asterine");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Asterine");
                if (!dir.exists()) {
                    boolean ignored = dir.mkdirs();
                }
            }
        }
        ensureSubDirectories(dir);
        return dir;
    }

    private static void ensureSubDirectories(File mainDir) {
        if (mainDir != null && mainDir.exists()) {
            for (SubFolder folder : SubFolder.values()) {
                File subDir = new File(mainDir, folder.getFolderName());
                if (!subDir.exists()) {
                    boolean ignored = subDir.mkdirs();
                }
            }
        }
    }

    public static File getSubDir(Context context, SubFolder subFolder) {
        File parent = getAsterineDirectory(context);
        File subDir = new File(parent, subFolder.getFolderName());
        if (!subDir.exists()) {
            boolean ignored = subDir.mkdirs();
        }
        return subDir;
    }

    public static File getFileForOperation(Context context, String operationType, String fileName) {
        SubFolder folder = SubFolder.PDF;
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc") || lowerName.endsWith(".rtf") || lowerName.endsWith(".odt") || (operationType != null && operationType.equalsIgnoreCase("WORD"))) {
            folder = SubFolder.WORD;
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") || (operationType != null && operationType.equalsIgnoreCase("EXCEL"))) {
            folder = SubFolder.EXCEL;
        } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".log") || lowerName.endsWith(".md") || lowerName.endsWith(".xml") || lowerName.endsWith(".json") || (operationType != null && operationType.equalsIgnoreCase("TXT"))) {
            folder = SubFolder.TEXT;
        } else if (operationType != null) {
            String op = operationType.toUpperCase();
            if (op.contains("CONVERT") || op.contains("WORD_PDF") || op.contains("EXCEL_PDF")) {
                folder = SubFolder.CONVERTED;
            } else if (op.contains("SIGN")) {
                folder = SubFolder.SIGNED;
            } else if (op.contains("MERGE")) {
                folder = SubFolder.MERGED;
            } else if (op.contains("ARRANGE") || op.contains("SPLIT")) {
                folder = SubFolder.ORGANIZED;
            }
        }
        File subDir = getSubDir(context, folder);
        return getUniqueFile(subDir, fileName);
    }

    public static File getUniqueFile(File targetDir, String fileName) {
        File file = new File(targetDir, fileName);
        if (!file.exists()) {
            return file;
        }

        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(targetDir, baseName + "_" + count + extension);
            count++;
        }
        return file;
    }
}

