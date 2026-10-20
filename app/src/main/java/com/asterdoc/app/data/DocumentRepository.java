package com.asterdoc.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

        // Start completely empty for first time users - no pre-found sample documents
        if (documents == null) {
            documents = new ArrayList<>();
            saveDocuments();
        }
    }

    public void saveDocuments() {
        String json = gson.toJson(documents);
        prefs.edit().putString(KEY_DOCS, json).apply();
    }

    public List<DocumentItem> getAllDocuments() {
        List<DocumentItem> active = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash()) {
                active.add(item);
            }
        }
        return active;
    }

    public List<DocumentItem> getRecentDocuments() {
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash()) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getCreatedDocuments() {
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && "Created".equals(item.getCategory())) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getScannedDocuments() {
        List<DocumentItem> list = new ArrayList<>();
        for (DocumentItem item : documents) {
            if (!item.isTrash() && ("Scanned".equals(item.getCategory()) || "SCAN".equals(item.getType()))) {
                list.add(item);
            }
        }
        return list;
    }

    public List<DocumentItem> getFavoriteDocuments() {
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

    public void addDocument(DocumentItem item) {
        documents.add(0, item);
        saveDocuments();
    }

    public void updateDocument(DocumentItem item) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getId().equals(item.getId())) {
                documents.set(i, item);
                break;
            }
        }
        saveDocuments();
    }

    public void moveToTrash(String id) {
        for (DocumentItem item : documents) {
            if (item.getId().equals(id)) {
                item.setTrash(true);
                break;
            }
        }
        saveDocuments();
    }

    public void deletePermanently(String id) {
        documents.removeIf(item -> item.getId().equals(id));
        saveDocuments();
    }

    public void emptyTrash() {
        documents.removeIf(DocumentItem::isTrash);
        saveDocuments();
    }

    public void clearAllDocuments() {
        if (documents != null) {
            documents.clear();
            saveDocuments();
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
