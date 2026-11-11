package com.asterdoc.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DocumentRepository {
    private static final String PREF_NAME = "asterdoc_docs_pref";
    private static final String KEY_DOCS = "documents_list";
    private static DocumentRepository instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private List<DocumentItem> documents;

    private DocumentRepository(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadDocuments();
    }

    public static synchronized DocumentRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DocumentRepository(context);
        }
        return instance;
    }

    private void loadDocuments() {
        String json = prefs.getString(KEY_DOCS, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<DocumentItem>>() {}.getType();
            documents = gson.fromJson(json, type);
        }

        if (documents == null) {
            documents = new ArrayList<>();
            saveDocuments();
        }

        new Thread(() -> {
            try {
                scanLocalDeviceDocuments();
            } catch (Exception ignored) {}
        }).start();
    }

    public synchronized void scanLocalDeviceDocuments() {
        try {
            File docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            List<File> searchDirs = new ArrayList<>();
            if (docsDir != null && docsDir.exists()) searchDirs.add(docsDir);
            if (downloadDir != null && downloadDir.exists()) searchDirs.add(downloadDir);

            for (File dir : searchDirs) {
                scanDirRecursive(dir, 0);
            }
        } catch (Exception ignored) {}
    }

    private void scanDirRecursive(File dir, int depth) {
        if (depth > 3 || dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursive(f, depth + 1);
            } else if (f.isFile() && f.length() > 0) {
                String name = f.getName().toLowerCase();
                String type = null;
                if (name.endsWith(".pdf")) type = "PDF";
                else if (name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".rtf") || name.endsWith(".odt") || name.endsWith(".dot") || name.endsWith(".docm") || name.endsWith(".dotx") || name.endsWith(".dotm")) type = "WORD";
                else if (name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")) type = "EXCEL";
                else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md") || name.endsWith(".xml") || name.endsWith(".json")) type = "TXT";

                if (type != null) {
                    boolean exists = false;
                    for (DocumentItem item : documents) {
                        if (item.getPath() != null && item.getPath().equalsIgnoreCase(f.getAbsolutePath())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        DocumentItem newItem = new DocumentItem(
                                String.valueOf(System.currentTimeMillis() + Math.random()),
                                f.getName(),
                                f.getAbsolutePath(),
                                type,
                                f.length(),
                                f.lastModified(),
                                "Recent"
                        );
                        documents.add(newItem);
                        saveDocuments();
                    }
                }
            }
        }
    }

    public void saveDocuments() {
        String json = gson.toJson(documents);
        prefs.edit().putString(KEY_DOCS, json).apply();
    }

    private long lastSyncTime = 0;

    private synchronized void syncMissingFiles() {
        if (documents == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSyncTime < 30000) { // Throttle disk checks to at most once per 30s
            return;
        }
        lastSyncTime = now;

        boolean modified = false;
        for (int i = documents.size() - 1; i >= 0; i--) {
            DocumentItem item = documents.get(i);
            if (!item.isTrash() && item.getPath() != null) {
                try {
                    File file = new File(item.getPath());
                    if (!file.exists() || file.length() == 0) {
                        documents.remove(i);
                        modified = true;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (modified) {
            saveDocuments();
        }
    }

    public List<DocumentItem> getAllDocuments() {
        syncMissingFiles();
        List<DocumentItem> active = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash()) {
                active.add(item);
            }
        }
        return active;
    }

    public List<DocumentItem> getRecentDocuments() {
        syncMissingFiles();
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash()) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getCreatedDocuments() {
        syncMissingFiles();
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && "Created".equals(item.getCategory())) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getScannedDocuments() {
        syncMissingFiles();
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && ("Scanned".equals(item.getCategory()) || "SCAN".equals(item.getType()))) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getFavoriteDocuments() {
        syncMissingFiles();
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && item.isFavorite()) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getTrashDocuments() {
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (item.isTrash()) {
                list.add(item);
            }
        }
        return list;
    }

    public interface OnRepositoryChangeListener {
        void onRepositoryChanged();
    }

    private final List<OnRepositoryChangeListener> listeners = new ArrayList<>();

    public synchronized void addListener(OnRepositoryChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(OnRepositoryChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private synchronized void notifyListeners() {
        for (OnRepositoryChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onRepositoryChanged();
            } catch (Exception ignored) {}
        }
    }

    public void addDocument(DocumentItem item) {
        documents.add(0, item);
        saveDocuments();
        notifyListeners();
    }

    public void updateDocument(DocumentItem item) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getId().equals(item.getId())) {
                documents.set(i, item);
                break;
            }
        }
        saveDocuments();
        notifyListeners();
    }

    public void moveToTrash(String id) {
        for (DocumentItem item : documents) {
            if (item.getId().equals(id)) {
                item.setTrash(true);
                break;
            }
        }
        saveDocuments();
        notifyListeners();
    }

    public void deletePermanently(String id) {
        for (int i = documents.size() - 1; i >= 0; i--) {
            DocumentItem item = documents.get(i);
            if (item.getId().equals(id)) {
                try {
                    if (item.getPath() != null) {
                        File file = new File(item.getPath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (Exception ignored) {}
                documents.remove(i);
                break;
            }
        }
        saveDocuments();
        notifyListeners();
    }

    public void emptyTrash() {
        for (int i = documents.size() - 1; i >= 0; i--) {
            DocumentItem item = documents.get(i);
            if (item.isTrash()) {
                try {
                    if (item.getPath() != null) {
                        File file = new File(item.getPath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                } catch (Exception ignored) {}
                documents.remove(i);
            }
        }
        saveDocuments();
        notifyListeners();
    }

    public void clearAllDocuments() {
        if (documents != null) {
            documents.clear();
            saveDocuments();
            notifyListeners();
        }
    }

    public List<DocumentItem> search(String query) {
        List<DocumentItem> results = new ArrayList<>();
        String q = query.toLowerCase();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && item.getName().toLowerCase().contains(q)) {
                results.add(item);
            }
        }
        return results;
    }
}

