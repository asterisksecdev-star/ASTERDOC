package com.asterdoc.app.ui.viewer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.asterdoc.app.R;
import com.asterdoc.app.data.DocumentItem;
import com.asterdoc.app.data.DocumentRepository;
import com.asterdoc.app.pdf.PdfProcessor;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ExcelViewerActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    private TableLayout tableSpreadsheet;
    private TextView tvNameBox;
    private TextView tvFormulaBar;
    private HorizontalScrollView scrollSheetTabs;
    private LinearLayout layoutSheetTabs;
    private View dividerSheetTabs;

    private File excelFile;
    private SpreadsheetLoader.SpreadsheetModel spreadsheetModel;
    private final List<TextView> selectedCells = new ArrayList<>();

    private float scaleFactor = 1.15f;
    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_excel_viewer);

        String filePath = getIntent().getStringExtra("FILE_PATH");
        if (filePath != null) {
            excelFile = new File(filePath);
        } else if (getIntent().getData() != null) {
            excelFile = getFileFromUri(getIntent().getData());
        }

        if (excelFile == null || !excelFile.exists() || excelFile.length() == 0) {
            Toast.makeText(this, "Spreadsheet file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbarExcel);
        toolbar.setTitle(excelFile.getName());
        toolbar.setNavigationOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        tableSpreadsheet = findViewById(R.id.tableSpreadsheet);
        tvNameBox = findViewById(R.id.tvNameBox);
        tvFormulaBar = findViewById(R.id.tvFormulaBar);
        scrollSheetTabs = findViewById(R.id.scrollSheetTabs);
        layoutSheetTabs = findViewById(R.id.layoutSheetTabs);
        dividerSheetTabs = findViewById(R.id.dividerSheetTabs);

        MaterialButton btnExport = findViewById(R.id.btnExportExcelPdf);

        View bottomActions = (View) btnExport.getParent();
        if (bottomActions != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomActions, (v, insets) -> {
                Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBars.bottom + 12);
                return insets;
            });
        }

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.5f));
                tableSpreadsheet.setScaleX(scaleFactor);
                tableSpreadsheet.setScaleY(scaleFactor);
                tableSpreadsheet.setPivotX(0f);
                tableSpreadsheet.setPivotY(0f);
                return true;
            }
        });

        tableSpreadsheet.setScaleX(scaleFactor);
        tableSpreadsheet.setScaleY(scaleFactor);
        tableSpreadsheet.setPivotX(0f);
        tableSpreadsheet.setPivotY(0f);

        loadSpreadsheetData();

        btnExport.setOnClickListener(v -> {
            btnExport.setEnabled(false);
            Toast.makeText(this, "Exporting spreadsheet to PDF...", Toast.LENGTH_SHORT).show();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    File pdf = PdfProcessor.excelToPdf(this, excelFile, excelFile.getName().replaceFirst("\\.[^.]+$", "") + "_Report.pdf");
                    runOnUiThread(() -> {
                        btnExport.setEnabled(true);
                        Intent intent = new Intent(this, PdfViewerActivity.class);
                        intent.putExtra("FILE_PATH", pdf.getAbsolutePath());
                        startActivity(intent);
                        Toast.makeText(this, "Spreadsheet exported as PDF!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnExport.setEnabled(true);
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        setFullscreenMode(true);
    }

    private void loadSpreadsheetData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                spreadsheetModel = SpreadsheetLoader.loadSpreadsheet(excelFile);
                runOnUiThread(() -> {
                    setupSheetTabs();
                    renderActiveSheet();
                });
            } catch (Exception e) {
                Log.e("ExcelViewerActivity", "Error loading spreadsheet: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Spreadsheet Error")
                            .setMessage("Unable to open or parse spreadsheet file:\n" + e.getMessage())
                            .setPositiveButton(R.string.close, (d, w) -> finish())
                            .setCancelable(false)
                            .show();
                });
            }
        });
    }

    private void setupSheetTabs() {
        if (spreadsheetModel == null || spreadsheetModel.sheets.size() <= 1) {
            scrollSheetTabs.setVisibility(View.GONE);
            dividerSheetTabs.setVisibility(View.GONE);
            return;
        }

        scrollSheetTabs.setVisibility(View.VISIBLE);
        dividerSheetTabs.setVisibility(View.VISIBLE);
        layoutSheetTabs.removeAllViews();

        for (int i = 0; i < spreadsheetModel.sheets.size(); i++) {
            final int sheetIndex = i;
            SpreadsheetLoader.SpreadsheetSheet sheet = spreadsheetModel.sheets.get(i);

            TextView tabTv = new TextView(this);
            tabTv.setText(sheet.sheetName);
            tabTv.setPadding(28, 10, 28, 10);
            tabTv.setTextSize(13.5f);
            tabTv.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            lp.setMargins(6, 0, 6, 0);
            tabTv.setLayoutParams(lp);

            if (i == spreadsheetModel.activeSheetIndex) {
                tabTv.setTextColor(Color.parseColor("#107C41"));
                tabTv.setTypeface(null, Typeface.BOLD);
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setColor(Color.parseColor("#FFFFFF"));
                activeBg.setStroke(2, Color.parseColor("#107C41"));
                activeBg.setCornerRadius(8f);
                tabTv.setBackground(activeBg);
            } else {
                tabTv.setTextColor(Color.parseColor("#555555"));
                tabTv.setTypeface(null, Typeface.NORMAL);
                GradientDrawable inactiveBg = new GradientDrawable();
                inactiveBg.setColor(Color.parseColor("#DCDCDC"));
                inactiveBg.setCornerRadius(8f);
                tabTv.setBackground(inactiveBg);
            }

            tabTv.setOnClickListener(v -> {
                spreadsheetModel.activeSheetIndex = sheetIndex;
                setupSheetTabs();
                renderActiveSheet();
            });

            layoutSheetTabs.addView(tabTv);
        }
    }

    private void renderActiveSheet() {
        if (spreadsheetModel == null) return;
        SpreadsheetLoader.SpreadsheetSheet sheet = spreadsheetModel.getActiveSheet();

        tableSpreadsheet.removeAllViews();
        selectedCells.clear();
        tvNameBox.setText("A1");
        tvFormulaBar.setText("Select a cell or row to view details");

        int totalCols = Math.min(Math.max(sheet.maxCols, 1), 60);
        int totalRows = Math.min(Math.max(sheet.maxRows, 1), 500);

        // Header Row (A, B, C...)
        TableRow headerRow = new TableRow(this);
        TextView cornerTv = createHeaderCellView(" # ", false);
        cornerTv.setOnClickListener(v -> selectAllCells());
        headerRow.addView(cornerTv);

        for (int c = 0; c < totalCols; c++) {
            if (sheet.hiddenColumns.contains(c)) continue;

            final int colIndex = c;
            final String colName = getColumnName(c);
            int colWidth = sheet.columnWidths.getOrDefault(c, 200);

            TextView colTv = createHeaderCellView(colName, false);
            colTv.setWidth(colWidth);
            colTv.setOnClickListener(v -> selectColumn(colIndex, colName));
            headerRow.addView(colTv);
        }
        tableSpreadsheet.addView(headerRow);

        // Render rows in non-blocking 25-row chunks
        renderRowChunk(sheet, 0, 25, totalRows, totalCols);
    }

    private void renderRowChunk(SpreadsheetLoader.SpreadsheetSheet sheet, int startRow, int chunkSize, int totalRows, int totalCols) {
        int endRow = Math.min(startRow + chunkSize, totalRows);

        for (int r = startRow; r < endRow; r++) {
            if (sheet.hiddenRows.contains(r)) continue;

            TableRow dataRow = new TableRow(this);
            final int rowIndex = r;

            if (sheet.rowHeights.containsKey(r)) {
                dataRow.setMinimumHeight(sheet.rowHeights.get(r));
            }

            // Row Number Header Column
            TextView numTv = createHeaderCellView(String.valueOf(r + 1), true);
            numTv.setOnClickListener(v -> selectRow(rowIndex));
            dataRow.addView(numTv);

            for (int c = 0; c < totalCols; c++) {
                if (sheet.hiddenColumns.contains(c)) continue;

                if (sheet.isMergedChild(r, c)) {
                    continue;
                }

                SpreadsheetLoader.CellData cellData = sheet.getCell(r, c);
                String val = cellData != null ? cellData.value : "";

                TextView cellTv = createDataCellView(cellData, val);

                int colWidth = sheet.columnWidths.getOrDefault(c, 200);

                SpreadsheetLoader.CellMergeRegion mergeRoot = sheet.getMergeRoot(r, c);
                if (mergeRoot != null) {
                    int combinedWidth = 0;
                    for (int sc = mergeRoot.firstCol; sc <= mergeRoot.lastCol; sc++) {
                        combinedWidth += sheet.columnWidths.getOrDefault(sc, 200);
                    }
                    colWidth = combinedWidth;

                    int rowSpan = mergeRoot.getRowSpan();
                    if (rowSpan > 1) {
                        int combinedHeight = 0;
                        for (int sr = mergeRoot.firstRow; sr <= mergeRoot.lastRow; sr++) {
                            Integer h = sheet.rowHeights.get(sr);
                            combinedHeight += (h != null ? h : 100);
                        }
                        cellTv.setMinimumHeight(combinedHeight);
                    }
                }

                cellTv.setWidth(colWidth);

                final String coord = getColumnName(c) + (r + 1);
                final SpreadsheetLoader.CellData finalCellData = cellData;

                cellTv.setOnClickListener(v -> selectSingleCell(cellTv, coord, val, finalCellData));

                dataRow.addView(cellTv);
            }
            tableSpreadsheet.addView(dataRow);
        }

        if (endRow < totalRows) {
            tableSpreadsheet.postDelayed(() -> renderRowChunk(sheet, endRow, chunkSize, totalRows, totalCols), 6);
        }
    }

    private TextView createHeaderCellView(String text, boolean isRowNo) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(24, 18, 24, 18);
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);

        if (isRowNo) {
            tv.setWidth(120);
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#EAEAEA"));
        drawable.setStroke(2, Color.parseColor("#C0C0C0"));
        tv.setBackground(drawable);

        tv.setTextColor(Color.parseColor("#107C41"));
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private TextView createDataCellView(SpreadsheetLoader.CellData cellData, String val) {
        TextView tv = new TextView(this);
        tv.setText(val != null ? val : "");
        tv.setPadding(24, 18, 24, 18);
        tv.setMinHeight(100);

        if (cellData != null) {
            tv.setTextSize(Math.max(12f, Math.min(cellData.fontSize, 20f)));

            int style = Typeface.NORMAL;
            if (cellData.isBold && cellData.isItalic) style = Typeface.BOLD_ITALIC;
            else if (cellData.isBold) style = Typeface.BOLD;
            else if (cellData.isItalic) style = Typeface.ITALIC;
            tv.setTypeface(null, style);

            tv.setTextColor(cellData.textColor != 0 ? cellData.textColor : Color.parseColor("#111111"));

            if (cellData.wrapText) {
                tv.setSingleLine(false);
            } else {
                tv.setSingleLine(true);
            }

            int gravity = Gravity.CENTER_VERTICAL;
            if (cellData.hAlignment == 1) gravity |= Gravity.CENTER_HORIZONTAL;
            else if (cellData.hAlignment == 2) gravity |= Gravity.END;
            else gravity |= Gravity.START;

            if (cellData.vAlignment == 0) gravity |= Gravity.TOP;
            else if (cellData.vAlignment == 2) gravity |= Gravity.BOTTOM;

            tv.setGravity(gravity);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            if (cellData.backgroundColor != Color.TRANSPARENT && cellData.backgroundColor != 0) {
                drawable.setColor(cellData.backgroundColor);
            } else if (cellData.isHeader) {
                drawable.setColor(Color.parseColor("#F5F5F5"));
            } else {
                drawable.setColor(Color.parseColor("#FFFFFF"));
            }
            drawable.setStroke(1, Color.parseColor("#D1D1D1"));
            tv.setBackground(drawable);
        } else {
            tv.setTextSize(14f);
            tv.setTextColor(Color.parseColor("#111111"));
            tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            tv.setSingleLine(true);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(Color.parseColor("#FFFFFF"));
            drawable.setStroke(1, Color.parseColor("#D1D1D1"));
            tv.setBackground(drawable);
        }

        return tv;
    }

    private void clearSelection() {
        for (TextView tv : selectedCells) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(Color.parseColor("#FFFFFF"));
            drawable.setStroke(1, Color.parseColor("#D1D1D1"));
            tv.setBackground(drawable);
            tv.setTextColor(Color.parseColor("#111111"));
        }
        selectedCells.clear();
    }

    private void selectSingleCell(TextView cellTv, String coord, String val, SpreadsheetLoader.CellData cellData) {
        clearSelection();
        tvNameBox.setText(coord);

        if (cellData != null && cellData.formula != null && !cellData.formula.isEmpty()) {
            tvFormulaBar.setText("=" + cellData.formula + "  (" + val + ")");
        } else {
            tvFormulaBar.setText(val != null && !val.isEmpty() ? val : "Empty Cell");
        }
        tvFormulaBar.setSelected(true);

        GradientDrawable selectedDrawable = new GradientDrawable();
        selectedDrawable.setShape(GradientDrawable.RECTANGLE);
        selectedDrawable.setColor(Color.parseColor("#E2F2E9"));
        selectedDrawable.setStroke(3, Color.parseColor("#107C41"));
        cellTv.setBackground(selectedDrawable);
        cellTv.setTextColor(Color.parseColor("#107C41"));
        selectedCells.add(cellTv);
    }

    private void selectColumn(int colIndex, String colName) {
        clearSelection();
        tvNameBox.setText("Col " + colName);
        tvFormulaBar.setText("Column " + colName + " Selected");

        int rowCount = tableSpreadsheet.getChildCount();
        for (int r = 1; r < rowCount; r++) {
            View rowView = tableSpreadsheet.getChildAt(r);
            if (rowView instanceof TableRow) {
                TableRow row = (TableRow) rowView;
                int childIndex = colIndex + 1;
                if (childIndex < row.getChildCount()) {
                    View cell = row.getChildAt(childIndex);
                    if (cell instanceof TextView) {
                        TextView cellTv = (TextView) cell;
                        GradientDrawable drawable = new GradientDrawable();
                        drawable.setShape(GradientDrawable.RECTANGLE);
                        drawable.setColor(Color.parseColor("#E2F2E9"));
                        drawable.setStroke(3, Color.parseColor("#107C41"));
                        cellTv.setBackground(drawable);
                        cellTv.setTextColor(Color.parseColor("#107C41"));
                        selectedCells.add(cellTv);
                    }
                }
            }
        }
    }

    private void selectRow(int rowIndex) {
        clearSelection();
        tvNameBox.setText("Row " + (rowIndex + 1));
        tvFormulaBar.setText("Row " + (rowIndex + 1) + " Selected");

        int childRowIndex = rowIndex + 1;
        if (childRowIndex < tableSpreadsheet.getChildCount()) {
            View rowView = tableSpreadsheet.getChildAt(childRowIndex);
            if (rowView instanceof TableRow) {
                TableRow row = (TableRow) rowView;
                int cellCount = row.getChildCount();
                for (int c = 1; c < cellCount; c++) {
                    View cell = row.getChildAt(c);
                    if (cell instanceof TextView) {
                        TextView cellTv = (TextView) cell;
                        GradientDrawable drawable = new GradientDrawable();
                        drawable.setShape(GradientDrawable.RECTANGLE);
                        drawable.setColor(Color.parseColor("#E2F2E9"));
                        drawable.setStroke(3, Color.parseColor("#107C41"));
                        cellTv.setBackground(drawable);
                        cellTv.setTextColor(Color.parseColor("#107C41"));
                        selectedCells.add(cellTv);
                    }
                }
            }
        }
    }

    private void selectAllCells() {
        clearSelection();
        tvNameBox.setText("All");
        tvFormulaBar.setText("Entire Sheet Selected");

        int rowCount = tableSpreadsheet.getChildCount();
        for (int r = 1; r < rowCount; r++) {
            View rowView = tableSpreadsheet.getChildAt(r);
            if (rowView instanceof TableRow) {
                TableRow row = (TableRow) rowView;
                for (int c = 1; c < row.getChildCount(); c++) {
                    View cell = row.getChildAt(c);
                    if (cell instanceof TextView) {
                        TextView cellTv = (TextView) cell;
                        GradientDrawable drawable = new GradientDrawable();
                        drawable.setShape(GradientDrawable.RECTANGLE);
                        drawable.setColor(Color.parseColor("#E2F2E9"));
                        drawable.setStroke(3, Color.parseColor("#107C41"));
                        cellTv.setBackground(drawable);
                        cellTv.setTextColor(Color.parseColor("#107C41"));
                        selectedCells.add(cellTv);
                    }
                }
            }
        }
    }

    private void setFullscreenMode(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enable) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            if (enable) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private File getFileFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            String fileName = "document_" + System.currentTimeMillis();
            String extension = ".xlsx";

            if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String name = cursor.getString(nameIndex);
                            if (name != null) {
                                fileName = name;
                                int dot = name.lastIndexOf('.');
                                if (dot != -1) {
                                    extension = name.substring(dot);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else if (uri.getPath() != null) {
                File pathFile = new File(uri.getPath());
                fileName = pathFile.getName();
                int dot = fileName.lastIndexOf('.');
                if (dot != -1) {
                    extension = fileName.substring(dot);
                }
            }

            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;

            File tempFile = File.createTempFile("open_excel_", extension, getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            is.close();

            DocumentItem item = new DocumentItem(
                    String.valueOf(System.currentTimeMillis()),
                    fileName,
                    tempFile.getAbsolutePath(),
                    "EXCEL",
                    tempFile.length(),
                    System.currentTimeMillis(),
                    "Recent"
            );
            DocumentRepository.getInstance(this).addDocument(item);

            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }

    private String getColumnName(int index) {
        StringBuilder sb = new StringBuilder();
        while (index >= 0) {
            sb.insert(0, (char) ('A' + (index % 26)));
            index = (index / 26) - 1;
        }
        return sb.toString();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }
}
