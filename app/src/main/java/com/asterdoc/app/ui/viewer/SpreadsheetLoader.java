package com.asterdoc.app.ui.viewer;

import android.graphics.Color;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SpreadsheetLoader {

    public static class CellMergeRegion {
        public int firstRow, lastRow, firstCol, lastCol;

        public CellMergeRegion(int firstRow, int lastRow, int firstCol, int lastCol) {
            this.firstRow = firstRow;
            this.lastRow = lastRow;
            this.firstCol = firstCol;
            this.lastCol = lastCol;
        }

        public int getRowSpan() {
            return lastRow - firstRow + 1;
        }

        public int getColSpan() {
            return lastCol - firstCol + 1;
        }
    }

    public static class CellData {
        public String value = "";
        public String formula = "";
        public int textColor = Color.parseColor("#111111");
        public int backgroundColor = Color.TRANSPARENT;
        public boolean isBold = false;
        public boolean isItalic = false;
        public boolean isUnderline = false;
        public float fontSize = 14f;
        public int hAlignment = 0; // 0=left, 1=center, 2=right
        public int vAlignment = 0; // 0=top, 1=center, 2=bottom
        public boolean wrapText = false;
        public boolean isHeader = false;
    }

    public static class SpreadsheetSheet {
        public String sheetName = "Sheet 1";
        public int maxRows = 0;
        public int maxCols = 0;
        public Map<Integer, Integer> columnWidths = new HashMap<>(); // col -> width in px
        public Map<Integer, Integer> rowHeights = new HashMap<>(); // row -> height in px
        public Set<Integer> hiddenColumns = new HashSet<>();
        public Set<Integer> hiddenRows = new HashSet<>();
        public List<CellMergeRegion> mergeRegions = new ArrayList<>();
        public Map<String, CellData> cellMap = new HashMap<>(); // "row,col" -> CellData

        public CellData getCell(int r, int c) {
            return cellMap.get(r + "," + c);
        }

        public boolean isMergedChild(int r, int c) {
            for (CellMergeRegion mr : mergeRegions) {
                if (r >= mr.firstRow && r <= mr.lastRow && c >= mr.firstCol && c <= mr.lastCol) {
                    if (r != mr.firstRow || c != mr.firstCol) {
                        return true;
                    }
                }
            }
            return false;
        }

        public CellMergeRegion getMergeRoot(int r, int c) {
            for (CellMergeRegion mr : mergeRegions) {
                if (r == mr.firstRow && c == mr.firstCol) {
                    return mr;
                }
            }
            return null;
        }
    }

    public static class SpreadsheetModel {
        public List<SpreadsheetSheet> sheets = new ArrayList<>();
        public int activeSheetIndex = 0;

        public SpreadsheetSheet getActiveSheet() {
            if (sheets.isEmpty()) return new SpreadsheetSheet();
            if (activeSheetIndex < 0 || activeSheetIndex >= sheets.size()) activeSheetIndex = 0;
            return sheets.get(activeSheetIndex);
        }
    }

    public static SpreadsheetModel loadSpreadsheet(File file) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) {
            throw new FileNotFoundException("Spreadsheet file does not exist or is empty.");
        }

        String fileName = file.getName().toLowerCase();

        // 1. Try Apache POI for native Excel formats (.xlsx, .xls, .xlsm, .xltx, .xltm, .xlam, .xla, .xlm)
        try {
            Workbook workbook = WorkbookFactory.create(file);
            SpreadsheetModel model = parsePoiWorkbook(workbook);
            workbook.close();
            if (model != null && !model.sheets.isEmpty()) {
                return model;
            }
        } catch (Throwable e) {
            Log.d("SpreadsheetLoader", "POI parse fallback for " + fileName + ": " + e.getMessage());
        }

        // 2. Direct OpenXML ZIP parser fallback for .xlsx / .xlsm / templates (bypasses StAX / XMLEventFactory missing class on Android)
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xlsm") || fileName.endsWith(".xltx") || fileName.endsWith(".xltm") || isZipArchive(file)) {
            try {
                SpreadsheetModel model = parseOpenXmlZipDirect(file);
                if (model != null && !model.sheets.isEmpty()) {
                    return model;
                }
            } catch (Throwable e) {
                Log.d("SpreadsheetLoader", "Direct OpenXML parse fallback failed: " + e.getMessage());
            }
        }

        // 3. Try XML Spreadsheet (Excel 2003 XML format)
        if (fileName.endsWith(".xml") || isXmlSpreadsheet(file)) {
            SpreadsheetModel model = parseXmlSpreadsheet(file);
            if (model != null && !model.sheets.isEmpty()) {
                return model;
            }
            if (fileName.endsWith(".xml")) {
                throw new IllegalArgumentException("The XML file is not a valid Excel 2003 spreadsheet document.");
            }
        }

        // 4. Try ODS (OpenDocument Spreadsheet)
        if (fileName.endsWith(".ods") || isOdsArchive(file)) {
            SpreadsheetModel model = parseOdsSpreadsheet(file);
            if (model != null && !model.sheets.isEmpty()) {
                return model;
            }
        }

        // 5. Try CSV / TSV / Delimited text format
        SpreadsheetModel csvModel = parseCsvSpreadsheet(file);
        if (csvModel != null && !csvModel.sheets.isEmpty()) {
            return csvModel;
        }

        throw new IOException("Unable to parse spreadsheet file. The file format may be password-protected, encrypted, or corrupted.");
    }

    private static SpreadsheetModel parsePoiWorkbook(Workbook workbook) {
        SpreadsheetModel model = new SpreadsheetModel();
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = null;
        try {
            evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        } catch (Throwable ignored) {}

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            SpreadsheetSheet modelSheet = new SpreadsheetSheet();
            modelSheet.sheetName = sheet.getSheetName();

            try {
                for (int m = 0; m < sheet.getNumMergedRegions(); m++) {
                    CellRangeAddress cra = sheet.getMergedRegion(m);
                    modelSheet.mergeRegions.add(new CellMergeRegion(cra.getFirstRow(), cra.getLastRow(), cra.getFirstColumn(), cra.getLastColumn()));
                }
            } catch (Throwable ignored) {}

            int lastRowNum = sheet.getLastRowNum();
            int maxCols = 0;

            for (int r = 0; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                try {
                    if (row.getZeroHeight()) {
                        modelSheet.hiddenRows.add(r);
                    }
                    if (row.getHeightInPoints() > 0) {
                        modelSheet.rowHeights.put(r, (int) (row.getHeightInPoints() * 2.2f));
                    }
                } catch (Throwable ignored) {}

                int lastCellNum = row.getLastCellNum();
                if (lastCellNum > maxCols) maxCols = lastCellNum;

                for (int c = 0; c < lastCellNum; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;

                    CellData cellData = new CellData();

                    try {
                        if (!modelSheet.columnWidths.containsKey(c)) {
                            int poiWidth = sheet.getColumnWidth(c);
                            int pxWidth = Math.max(120, Math.min((int) (poiWidth / 32f), 600));
                            modelSheet.columnWidths.put(c, pxWidth);
                        }
                        if (sheet.isColumnHidden(c)) {
                            modelSheet.hiddenColumns.add(c);
                        }
                    } catch (Throwable ignored) {}

                    try {
                        if (evaluator != null && cell.getCellTypeEnum() == CellType.FORMULA) {
                            cellData.formula = cell.getCellFormula();
                            try {
                                cellData.value = formatter.formatCellValue(cell, evaluator);
                            } catch (Throwable e) {
                                cellData.value = cell.getCellFormula();
                            }
                        } else {
                            cellData.value = formatter.formatCellValue(cell);
                        }
                    } catch (Throwable e) {
                        try {
                            cellData.value = cell.toString();
                        } catch (Throwable ignored) {}
                    }

                    try {
                        CellStyle style = cell.getCellStyle();
                        if (style != null) {
                            try {
                                cellData.wrapText = style.getWrapText();
                            } catch (Throwable ignored) {}

                            try {
                                HorizontalAlignment hAlign = style.getAlignmentEnum();
                                if (hAlign == HorizontalAlignment.CENTER) cellData.hAlignment = 1;
                                else if (hAlign == HorizontalAlignment.RIGHT) cellData.hAlignment = 2;
                            } catch (Throwable ignored) {}

                            try {
                                VerticalAlignment vAlign = style.getVerticalAlignmentEnum();
                                if (vAlign == VerticalAlignment.CENTER) cellData.vAlignment = 1;
                                else if (vAlign == VerticalAlignment.BOTTOM) cellData.vAlignment = 2;
                            } catch (Throwable ignored) {}

                            try {
                                Font font = workbook.getFontAt(style.getFontIndex());
                                if (font != null) {
                                    cellData.isBold = font.getBold();
                                    cellData.isItalic = font.getItalic();
                                    if (font.getFontHeightInPoints() > 0) {
                                        cellData.fontSize = font.getFontHeightInPoints();
                                    }
                                }
                            } catch (Throwable ignored) {}

                            try {
                                cellData.backgroundColor = getSafeColor(style);
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}

                    if (cellData.value != null && !cellData.value.trim().isEmpty()) {
                        modelSheet.cellMap.put(r + "," + c, cellData);
                    }
                }
            }

            modelSheet.maxRows = lastRowNum + 1;
            modelSheet.maxCols = maxCols;
            model.sheets.add(modelSheet);
        }
        return model;
    }

    private static SpreadsheetModel parseOpenXmlZipDirect(File file) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry;
            String sharedStringsXml = null;
            Map<String, String> sheetXmls = new HashMap<>();

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equals(name)) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    sharedStringsXml = sb.toString();
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    sheetXmls.put(name, sb.toString());
                }
            }
            zis.close();

            List<String> sharedStrings = new ArrayList<>();
            if (sharedStringsXml != null) {
                int idx = 0;
                while (true) {
                    int start = sharedStringsXml.indexOf("<t", idx);
                    if (start == -1) break;
                    int close = sharedStringsXml.indexOf(">", start);
                    if (close == -1) break;
                    int end = sharedStringsXml.indexOf("</t>", close);
                    if (end == -1) break;
                    sharedStrings.add(sharedStringsXml.substring(close + 1, end));
                    idx = end + 4;
                }
            }

            if (sheetXmls.isEmpty()) return null;

            SpreadsheetModel model = new SpreadsheetModel();
            int sheetNum = 1;
            for (Map.Entry<String, String> sheetEntry : sheetXmls.entrySet()) {
                SpreadsheetSheet sheet = new SpreadsheetSheet();
                sheet.sheetName = "Sheet " + sheetNum++;
                String sheetXml = sheetEntry.getValue();

                List<List<String>> rows = new ArrayList<>();
                int rIdx = 0;
                while (true) {
                    int rStart = sheetXml.indexOf("<row", rIdx);
                    if (rStart == -1) break;
                    int rEnd = sheetXml.indexOf("</row>", rStart);
                    if (rEnd == -1) rEnd = sheetXml.indexOf("/>", rStart);
                    if (rEnd == -1) break;

                    String rowStr = sheetXml.substring(rStart, rEnd);
                    List<String> cells = new ArrayList<>();
                    int cIdx = 0;
                    while (true) {
                        int cStart = rowStr.indexOf("<c", cIdx);
                        if (cStart == -1) break;
                        int cEnd = rowStr.indexOf("</c>", cStart);
                        if (cEnd == -1) cEnd = rowStr.indexOf("/>", cStart);
                        if (cEnd == -1) break;

                        String cellStr = rowStr.substring(cStart, cEnd);
                        boolean isString = cellStr.contains("t=\"s\"");
                        int vStart = cellStr.indexOf("<v>");
                        int vEnd = cellStr.indexOf("</v>");
                        if (vStart != -1 && vEnd != -1) {
                            String val = cellStr.substring(vStart + 3, vEnd);
                            if (isString) {
                                try {
                                    int sIndex = Integer.parseInt(val);
                                    if (sIndex >= 0 && sIndex < sharedStrings.size()) {
                                        val = sharedStrings.get(sIndex);
                                    }
                                } catch (Exception ignored) {}
                            }
                            cells.add(val);
                        } else {
                            cells.add("");
                        }
                        cIdx = cEnd + 4;
                    }
                    if (!cells.isEmpty()) {
                        rows.add(cells);
                    }
                    rIdx = rEnd + 6;
                }

                int maxCols = 0;
                for (int r = 0; r < rows.size(); r++) {
                    List<String> rowList = rows.get(r);
                    if (rowList.size() > maxCols) maxCols = rowList.size();
                    for (int col = 0; col < rowList.size(); col++) {
                        String val = rowList.get(col);
                        if (val != null && !val.isEmpty()) {
                            CellData cd = new CellData();
                            cd.value = val;
                            sheet.cellMap.put(r + "," + col, cd);
                        }
                    }
                }
                sheet.maxRows = rows.size();
                sheet.maxCols = maxCols;
                model.sheets.add(sheet);
            }
            return model;
        } catch (Throwable e) {
            return null;
        }
    }

    private static int getSafeColor(CellStyle style) {
        try {
            if (style instanceof XSSFCellStyle) {
                XSSFColor xCol = ((XSSFCellStyle) style).getFillForegroundColorColor();
                if (xCol != null) {
                    byte[] rgb = xCol.getRGB();
                    if (rgb != null && rgb.length >= 3) {
                        int r = rgb[0] & 0xFF;
                        int g = rgb[1] & 0xFF;
                        int b = rgb[2] & 0xFF;
                        return Color.rgb(r, g, b);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Color.TRANSPARENT;
    }

    private static SpreadsheetModel parseCsvSpreadsheet(File file) throws Exception {
        SpreadsheetModel model = new SpreadsheetModel();
        SpreadsheetSheet sheet = new SpreadsheetSheet();
        sheet.sheetName = file.getName().replaceFirst("\\.[^.]+$", "");

        List<List<String>> rows = new ArrayList<>();

        char delimiter = ',';
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String sample = br.readLine();
            if (sample != null) {
                int commas = countOccurrences(sample, ',');
                int semicolons = countOccurrences(sample, ';');
                int tabs = countOccurrences(sample, '\t');
                int pipes = countOccurrences(sample, '|');

                if (semicolons > commas && semicolons > tabs) delimiter = ';';
                else if (tabs > commas) delimiter = '\t';
                else if (pipes > commas) delimiter = '|';
            }
        } catch (Throwable ignored) {}

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(String.valueOf(delimiter));
                List<String> r = new ArrayList<>();
                for (String p : parts) {
                    r.add(p.trim().replaceAll("^\"|\"$", "").replace("\"\"", "\""));
                }
                if (!r.isEmpty() && !(r.size() == 1 && r.get(0).isEmpty())) {
                    rows.add(r);
                }
            }
        }

        int maxCols = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<String> rowList = rows.get(r);
            if (rowList.size() > maxCols) maxCols = rowList.size();

            for (int col = 0; col < rowList.size(); col++) {
                String val = rowList.get(col);
                if (val != null && !val.isEmpty()) {
                    CellData cd = new CellData();
                    cd.value = val;
                    if (r == 0) cd.isHeader = true;
                    sheet.cellMap.put(r + "," + col, cd);
                }
            }
        }

        sheet.maxRows = rows.size();
        sheet.maxCols = maxCols;
        model.sheets.add(sheet);
        return model;
    }

    private static SpreadsheetModel parseXmlSpreadsheet(File file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);

            NodeList worksheetList = doc.getElementsByTagNameNS("*", "Worksheet");
            if (worksheetList.getLength() == 0) {
                worksheetList = doc.getElementsByTagName("Worksheet");
            }
            if (worksheetList.getLength() == 0) return null;

            SpreadsheetModel model = new SpreadsheetModel();

            for (int w = 0; w < worksheetList.getLength(); w++) {
                Element wsElem = (Element) worksheetList.item(w);
                SpreadsheetSheet sheet = new SpreadsheetSheet();
                String name = wsElem.getAttribute("ss:Name");
                if (name == null || name.isEmpty()) name = wsElem.getAttribute("Name");
                if (name == null || name.isEmpty()) name = "Sheet " + (w + 1);
                sheet.sheetName = name;

                NodeList rowList = wsElem.getElementsByTagNameNS("*", "Row");
                if (rowList.getLength() == 0) rowList = wsElem.getElementsByTagName("Row");

                int maxCols = 0;
                for (int r = 0; r < rowList.getLength(); r++) {
                    Element rowElem = (Element) rowList.item(r);
                    NodeList cellList = rowElem.getElementsByTagNameNS("*", "Cell");
                    if (cellList.getLength() == 0) cellList = rowElem.getElementsByTagName("Cell");

                    int colIdx = 0;
                    for (int c = 0; c < cellList.getLength(); c++) {
                        Element cellElem = (Element) cellList.item(c);
                        String indexAttr = cellElem.getAttribute("ss:Index");
                        if (indexAttr != null && !indexAttr.isEmpty()) {
                            try { colIdx = Integer.parseInt(indexAttr) - 1; } catch (Exception ignored) {}
                        }

                        NodeList dataList = cellElem.getElementsByTagNameNS("*", "Data");
                        if (dataList.getLength() == 0) dataList = cellElem.getElementsByTagName("Data");

                        if (dataList.getLength() > 0) {
                            String cellVal = dataList.item(0).getTextContent();
                            if (cellVal != null && !cellVal.isEmpty()) {
                                CellData cd = new CellData();
                                cd.value = cellVal;
                                sheet.cellMap.put(r + "," + colIdx, cd);
                            }
                        }
                        colIdx++;
                    }
                    if (colIdx > maxCols) maxCols = colIdx;
                }

                sheet.maxRows = rowList.getLength();
                sheet.maxCols = maxCols;
                model.sheets.add(sheet);
            }
            return model;
        } catch (Throwable e) {
            return null;
        }
    }

    private static SpreadsheetModel parseOdsSpreadsheet(File file) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            ZipEntry entry;
            String contentXml = null;

            while ((entry = zis.getNextEntry()) != null) {
                if ("content.xml".equals(entry.getName())) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    contentXml = sb.toString();
                    break;
                }
            }
            zis.close();

            if (contentXml == null) return null;

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(contentXml.getBytes(StandardCharsets.UTF_8)));

            NodeList tableList = doc.getElementsByTagNameNS("*", "table");
            if (tableList.getLength() == 0) tableList = doc.getElementsByTagName("table:table");
            if (tableList.getLength() == 0) return null;

            SpreadsheetModel model = new SpreadsheetModel();

            for (int t = 0; t < tableList.getLength(); t++) {
                Element tableElem = (Element) tableList.item(t);
                SpreadsheetSheet sheet = new SpreadsheetSheet();
                String name = tableElem.getAttribute("table:name");
                if (name == null || name.isEmpty()) name = "Sheet " + (t + 1);
                sheet.sheetName = name;

                NodeList rowList = tableElem.getElementsByTagNameNS("*", "table-row");
                if (rowList.getLength() == 0) rowList = tableElem.getElementsByTagName("table:table-row");

                int maxCols = 0;
                for (int r = 0; r < rowList.getLength(); r++) {
                    Element rowElem = (Element) rowList.item(r);
                    NodeList cellList = rowElem.getElementsByTagNameNS("*", "table-cell");
                    if (cellList.getLength() == 0) cellList = rowElem.getElementsByTagName("table:table-cell");

                    int colIdx = 0;
                    for (int c = 0; c < cellList.getLength(); c++) {
                        Element cellElem = (Element) cellList.item(c);

                        int repeat = 1;
                        String repeatAttr = cellElem.getAttribute("table:number-columns-repeated");
                        if (repeatAttr != null && !repeatAttr.isEmpty()) {
                            try { repeat = Math.min(Integer.parseInt(repeatAttr), 50); } catch (Exception ignored) {}
                        }

                        String textVal = cellElem.getTextContent();
                        if (textVal != null && !textVal.trim().isEmpty()) {
                            CellData cd = new CellData();
                            cd.value = textVal.trim();
                            sheet.cellMap.put(r + "," + colIdx, cd);
                        }
                        colIdx += repeat;
                    }
                    if (colIdx > maxCols) maxCols = colIdx;
                }

                sheet.maxRows = rowList.getLength();
                sheet.maxCols = maxCols;
                model.sheets.add(sheet);
            }
            return model;
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean isXmlSpreadsheet(File file) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buf = new char[512];
            int len = br.read(buf, 0, 512);
            if (len > 0) {
                String header = new String(buf, 0, len);
                return header.contains("urn:schemas-microsoft-com:office:spreadsheet") || header.contains("<Workbook");
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isOdsArchive(File file) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("mimetype".equals(entry.getName()) || "content.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isZipArchive(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read >= 4) {
                return header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int countOccurrences(String haystack, char needle) {
        int count = 0;
        for (int i = 0; i < haystack.length(); i++) {
            if (haystack.charAt(i) == needle) count++;
        }
        return count;
    }
}
