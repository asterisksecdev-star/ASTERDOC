package document;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import com.asterdoc.app.pdf.PdfProcessor;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class OfficeWordRenderer {

    private OfficeWordRenderer() {
    }

    public static class WordTableCell {
        public String text;
        public int gridSpan;
        public int bgColor;
        public int borderColor;

        public WordTableCell(String text, int gridSpan, int bgColor, int borderColor) {
            this.text = text != null ? text : "";
            this.gridSpan = Math.max(1, gridSpan);
            this.bgColor = bgColor;
            this.borderColor = borderColor;
        }
    }

    public static class WordTable {
        public List<List<WordTableCell>> rows = new ArrayList<>();
    }

    public static class WordElement {
        public enum Type { PARAGRAPH, TABLE, IMAGE }
        public Type type;
        public String paragraph;
        public WordTable table;
        public Bitmap image;

        public WordElement(String p) { this.type = Type.PARAGRAPH; this.paragraph = p; }
        public WordElement(WordTable t) { this.type = Type.TABLE; this.table = t; }
        public WordElement(Bitmap img) { this.type = Type.IMAGE; this.image = img; }
    }

    public static File convertToPdf(File sourceWord, File outputPdf) throws Exception {
        if (sourceWord == null || !sourceWord.exists() || sourceWord.length() == 0) {
            throw new IllegalArgumentException("Word document does not exist or is empty.");
        }

        // Force cache invalidation for table/xml parsing fix
        if (outputPdf.exists()) {
            boolean deleted = outputPdf.delete();
            Log.d("OfficeWordRenderer", "Invalidated previous cache: " + deleted);
        }

        String name = sourceWord.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".odt")) {
            return convertOdtToPdf(sourceWord, outputPdf);
        }
        if (name.endsWith(".rtf")) {
            return convertRtfToPdf(sourceWord, outputPdf);
        }
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".mht") || name.endsWith(".mhtml")) {
            return convertHtmlToPdf(sourceWord, outputPdf);
        }
        if (name.endsWith(".xml")) {
            return convertXmlWordToPdf(sourceWord, outputPdf);
        }

        List<WordElement> elements = new ArrayList<>();
        List<Bitmap> extractedImages = new ArrayList<>();

        if (name.endsWith(".doc") || name.endsWith(".dot")) {
            List<String> rawLines = PdfProcessor.extractDocBinaryText(sourceWord);
            for (String line : rawLines) {
                if (isCleanTextNode(line)) {
                    elements.add(new WordElement(decodeXmlEntities(line)));
                }
            }
        } else {
            // .docx, .docm, .dotx, .dotm via Zip & Namespace-Aware XmlPullParser
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceWord))) {
                ZipEntry entry;
                String documentXml = null;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.equalsIgnoreCase("word/document.xml") || entryName.toLowerCase(Locale.ROOT).endsWith("document.xml")) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            baos.write(buf, 0, len);
                        }
                        documentXml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    } else if (entryName.startsWith("word/media/")) {
                        try {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = zis.read(buf)) > 0) {
                                baos.write(buf, 0, len);
                            }
                            byte[] imgBytes = baos.toByteArray();
                            Bitmap bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
                            if (bmp != null) extractedImages.add(bmp);
                        } catch (Exception ignored) {}
                    }
                }

                if (documentXml != null) {
                    parseStructuredWordXml(documentXml, elements, extractedImages);
                }
            } catch (Exception e) {
                Log.e("OfficeWordRenderer", "DOCX structured parsing failed, using binary text fallback", e);
                List<String> rawLines = PdfProcessor.extractDocBinaryText(sourceWord);
                for (String line : rawLines) {
                    if (isCleanTextNode(line)) {
                        elements.add(new WordElement(decodeXmlEntities(line)));
                    }
                }
            }
        }

        if (elements.isEmpty()) {
            throw new IllegalStateException("No readable content or tables found in Word document. The file may be password-protected or corrupted.");
        }

        return renderElementsToPdf(elements, outputPdf);
    }

    private static void parseStructuredWordXml(String documentXml, List<WordElement> elements, List<Bitmap> extractedImages) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new java.io.StringReader(documentXml));

            int eventType = parser.getEventType();
            WordTable currentTable = null;
            List<WordTableCell> currentRow = null;
            StringBuilder currentCellText = new StringBuilder();
            StringBuilder currentParaText = new StringBuilder();
            int gridSpan = 1;
            int cellBg = 0xFFFFFFFF;
            boolean inTable = false;
            boolean inCell = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (eventType == XmlPullParser.START_TAG) {
                    if ("tbl".equalsIgnoreCase(name)) {
                        if (currentParaText.length() > 0) {
                            String p = currentParaText.toString().trim();
                            if (isCleanTextNode(p)) {
                                elements.add(new WordElement(decodeXmlEntities(p)));
                            }
                            currentParaText.setLength(0);
                        }
                        inTable = true;
                        currentTable = new WordTable();
                    } else if ("tr".equalsIgnoreCase(name) && inTable) {
                        currentRow = new ArrayList<>();
                    } else if ("tc".equalsIgnoreCase(name) && inTable) {
                        inCell = true;
                        gridSpan = 1;
                        cellBg = 0xFFFFFFFF;
                        currentCellText.setLength(0);
                    } else if ("gridSpan".equalsIgnoreCase(name) && inCell) {
                        String val = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val");
                        if (val == null) val = parser.getAttributeValue(null, "w:val");
                        if (val != null) {
                            try { gridSpan = Integer.parseInt(val); } catch (Exception ignored) {}
                        }
                    } else if ("shd".equalsIgnoreCase(name)) {
                        String fill = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fill");
                        if (fill == null) fill = parser.getAttributeValue(null, "w:fill");
                        if (fill != null && !fill.equalsIgnoreCase("auto") && !fill.isEmpty()) {
                            try {
                                cellBg = Color.parseColor("#" + (fill.length() == 6 ? "FF" + fill : fill));
                            } catch (Exception ignored) {}
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    String text = parser.getText();
                    if (text != null && !text.isEmpty()) {
                        if (inCell) {
                            currentCellText.append(text);
                        } else {
                            currentParaText.append(text);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("tbl".equalsIgnoreCase(name)) {
                        inTable = false;
                        if (currentTable != null && !currentTable.rows.isEmpty()) {
                            elements.add(new WordElement(currentTable));
                        }
                        currentTable = null;
                    } else if ("tr".equalsIgnoreCase(name) && inTable) {
                        if (currentRow != null && !currentRow.isEmpty()) {
                            currentTable.rows.add(currentRow);
                        }
                        currentRow = null;
                    } else if ("tc".equalsIgnoreCase(name) && inTable) {
                        inCell = false;
                        if (currentRow != null) {
                            String cellContent = currentCellText.toString().trim();
                            currentRow.add(new WordTableCell(decodeXmlEntities(cellContent), gridSpan, cellBg, 0xFFB0B0B0));
                        }
                        currentCellText.setLength(0);
                    } else if ("p".equalsIgnoreCase(name) && !inTable) {
                        String p = currentParaText.toString().trim();
                        if (isCleanTextNode(p)) {
                            elements.add(new WordElement(decodeXmlEntities(p)));
                        }
                        currentParaText.setLength(0);
                    }
                }
                eventType = parser.next();
            }

            if (currentParaText.length() > 0) {
                String p = currentParaText.toString().trim();
                if (isCleanTextNode(p)) {
                    elements.add(new WordElement(decodeXmlEntities(p)));
                }
            }
        } catch (Exception e) {
            Log.e("OfficeWordRenderer", "XmlPullParser failed, falling back to <w:t> extraction", e);
            int idx = 0;
            while (idx < documentXml.length()) {
                int start = documentXml.indexOf("<w:t", idx);
                if (start == -1) break;
                int close = documentXml.indexOf(">", start);
                if (close == -1) break;
                int end = documentXml.indexOf("</w:t>", close);
                if (end == -1) break;
                String t = documentXml.substring(close + 1, end).trim();
                if (isCleanTextNode(t)) {
                    elements.add(new WordElement(decodeXmlEntities(t)));
                }
                idx = end + 6;
            }
        }
    }

    private static boolean isCleanTextNode(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        String s = str.trim();
        return !s.contains("<w:") && !s.contains("</w:") && !s.contains("w:p") && !s.contains("w:tbl") && !s.contains("w:tr") && !s.contains("w:tc") && !s.contains("w:r") && !s.contains("xmlns:") && !s.startsWith("<");
    }

    private static String decodeXmlEntities(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&#160;", " ");
    }

    private static File renderElementsToPdf(List<WordElement> elements, File outputPdf) throws Exception {
        PdfDocument pdfDocument = new PdfDocument();
        Paint textPaint = new Paint();
        textPaint.setColor(0xFF1F1F1F);
        textPaint.setTextSize(13f);
        textPaint.setAntiAlias(true);

        Paint headingPaint = new Paint();
        headingPaint.setColor(0xFF111111);
        headingPaint.setTextSize(15f);
        headingPaint.setFakeBoldText(true);
        headingPaint.setAntiAlias(true);

        Paint tableTextPaint = new Paint();
        tableTextPaint.setColor(0xFF222222);
        tableTextPaint.setTextSize(12f);
        tableTextPaint.setAntiAlias(true);

        Paint borderPaint = new Paint();
        borderPaint.setColor(0xFFB0B0B0);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.0f);

        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 54;
        int printableWidth = pageWidth - (margin * 2);
        int y = 60;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        int pageNum = 1;

        for (WordElement elem : elements) {
            if (elem.type == WordElement.Type.PARAGRAPH) {
                if (y > pageHeight - 60) {
                    pdfDocument.finishPage(page);
                    pageNum++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
                String p = elem.paragraph;
                Paint curPaint = textPaint;
                if (p.length() < 90 && (p.startsWith("Question") || p.startsWith("Section") || p.startsWith("Chapter") || p.startsWith("Table") || p.startsWith("Figure") || (p.equals(p.toUpperCase(Locale.ROOT)) && p.length() > 3 && !p.contains(" ")))) {
                    curPaint = headingPaint;
                }

                String[] words = p.split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String w : words) {
                    if (curPaint.measureText(line + " " + w) <= printableWidth) {
                        if (line.length() > 0) line.append(" ");
                        line.append(w);
                    } else {
                        if (line.length() > 0) {
                            canvas.drawText(line.toString(), margin, y, curPaint);
                            y += 22;
                            if (y > pageHeight - 60) {
                                pdfDocument.finishPage(page);
                                pageNum++;
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                                page = pdfDocument.startPage(pageInfo);
                                canvas = page.getCanvas();
                                y = 60;
                            }
                        }
                        line = new StringBuilder(w);
                    }
                }
                if (line.length() > 0) {
                    canvas.drawText(line.toString(), margin, y, curPaint);
                    y += 28;
                }
            } else if (elem.type == WordElement.Type.TABLE) {
                WordTable wt = elem.table;
                if (wt.rows.isEmpty()) continue;

                int maxCols = 1;
                for (List<WordTableCell> row : wt.rows) {
                    int cSum = 0;
                    for (WordTableCell tc : row) cSum += tc.gridSpan;
                    if (cSum > maxCols) maxCols = cSum;
                }
                float colW = (float) printableWidth / maxCols;

                for (List<WordTableCell> row : wt.rows) {
                    float rowH = 32f;
                    List<List<String>> rowCellLines = new ArrayList<>();
                    for (WordTableCell tc : row) {
                        float cellW = colW * tc.gridSpan - 12f;
                        List<String> lines = new ArrayList<>();
                        if (tc.text != null && !tc.text.isEmpty()) {
                            String[] words = tc.text.split("\\s+");
                            StringBuilder curLine = new StringBuilder();
                            for (String w : words) {
                                if (tableTextPaint.measureText(curLine + " " + w) <= cellW) {
                                    if (curLine.length() > 0) curLine.append(" ");
                                    curLine.append(w);
                                } else {
                                    if (curLine.length() > 0) lines.add(curLine.toString());
                                    curLine = new StringBuilder(w);
                                }
                            }
                            if (curLine.length() > 0) lines.add(curLine.toString());
                        }
                        rowCellLines.add(lines);
                        float neededH = Math.max(32f, lines.size() * 16f + 12f);
                        if (neededH > rowH) rowH = neededH;
                    }

                    if (y + rowH > pageHeight - 60) {
                        pdfDocument.finishPage(page);
                        pageNum++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = 60;
                    }

                    float cellX = margin;
                    for (int c = 0; c < row.size(); c++) {
                        WordTableCell tc = row.get(c);
                        float cellW = colW * tc.gridSpan;
                        RectF cellRect = new RectF(cellX, y, cellX + cellW, y + rowH);

                        Paint bgPaint = new Paint();
                        bgPaint.setColor(tc.bgColor);
                        bgPaint.setStyle(Paint.Style.FILL);
                        canvas.drawRect(cellRect, bgPaint);
                        canvas.drawRect(cellRect, borderPaint);

                        List<String> lines = rowCellLines.get(c);
                        float lineY = y + 18f;
                        for (String l : lines) {
                            canvas.drawText(l, cellX + 6f, lineY, tableTextPaint);
                            lineY += 16f;
                        }
                        cellX += cellW;
                    }
                    y += (int) rowH;
                }
                y += 16;
            } else if (elem.type == WordElement.Type.IMAGE && elem.image != null) {
                Bitmap bmp = elem.image;
                if (y + 200 > pageHeight - 60) {
                    pdfDocument.finishPage(page);
                    pageNum++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
                try {
                    int imgW = Math.min(printableWidth, bmp.getWidth());
                    int imgH = Math.max(120, (imgW * bmp.getHeight()) / Math.max(1, bmp.getWidth()));
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, imgW, imgH, true);
                    canvas.drawBitmap(scaled, margin, y, null);
                    y += scaled.getHeight() + 20;
                } catch (Exception ignored) {}
            }
        }

        pdfDocument.finishPage(page);

        try (FileOutputStream fos = new FileOutputStream(outputPdf)) {
            pdfDocument.writeTo(fos);
        }
        pdfDocument.close();

        return outputPdf;
    }

    private static File convertOdtToPdf(File odtFile, File outputPdf) throws Exception {
        List<WordElement> elements = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(odtFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase("content.xml")) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    String xml = sb.toString();
                    int idx = 0;
                    while (true) {
                        int start = xml.indexOf("<text:p", idx);
                        if (start == -1) break;
                        int close = xml.indexOf(">", start);
                        if (close == -1) break;
                        int end = xml.indexOf("</text:p>", close);
                        if (end == -1) break;
                        String pText = xml.substring(close + 1, end).replaceAll("<[^>]*>", "").trim();
                        if (isCleanTextNode(pText)) {
                            elements.add(new WordElement(decodeXmlEntities(pText)));
                        }
                        idx = end + 9;
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception("Unable to parse ODT document: " + e.getMessage());
        }

        if (elements.isEmpty()) {
            elements.add(new WordElement(odtFile.getName()));
        }
        return renderElementsToPdf(elements, outputPdf);
    }

    private static File convertRtfToPdf(File rtfFile, File outputPdf) throws Exception {
        List<WordElement> elements = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(rtfFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String cleaned = line.replaceAll("\\[a-z0-9]+(-?[0-9]+)?[ ]?", "").replaceAll("[{}]", "").trim();
                if (isCleanTextNode(cleaned)) {
                    elements.add(new WordElement(decodeXmlEntities(cleaned)));
                }
            }
        } catch (Exception e) {
            throw new Exception("Unable to parse RTF document: " + e.getMessage());
        }
        if (elements.isEmpty()) {
            elements.add(new WordElement(rtfFile.getName()));
        }
        return renderElementsToPdf(elements, outputPdf);
    }

    private static File convertHtmlToPdf(File htmlFile, File outputPdf) throws Exception {
        List<WordElement> elements = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(htmlFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String html = sb.toString().replaceAll("<[^>]*>", "\n");
            String[] lines = html.split("\n");
            for (String l : lines) {
                String trimmed = l.trim();
                if (isCleanTextNode(trimmed)) {
                    elements.add(new WordElement(decodeXmlEntities(trimmed)));
                }
            }
        } catch (Exception e) {
            throw new Exception("Unable to parse HTML document: " + e.getMessage());
        }
        if (elements.isEmpty()) {
            elements.add(new WordElement(htmlFile.getName()));
        }
        return renderElementsToPdf(elements, outputPdf);
    }

    private static File convertXmlWordToPdf(File xmlFile, File outputPdf) throws Exception {
        List<WordElement> elements = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String xml = sb.toString();
            int idx = 0;
            while (idx < xml.length()) {
                int start = xml.indexOf("<w:t", idx);
                if (start == -1) {
                    start = xml.indexOf("<t", idx);
                    if (start == -1) break;
                }
                int close = xml.indexOf(">", start);
                if (close == -1) break;
                int end = xml.indexOf("</", close);
                if (end == -1) break;
                String t = xml.substring(close + 1, end).trim();
                if (isCleanTextNode(t)) {
                    elements.add(new WordElement(decodeXmlEntities(t)));
                }
                idx = end + 4;
            }
        } catch (Exception e) {
            throw new Exception("Unable to parse Word XML document: " + e.getMessage());
        }
        if (elements.isEmpty()) {
            elements.add(new WordElement(xmlFile.getName()));
        }
        return renderElementsToPdf(elements, outputPdf);
    }
}
