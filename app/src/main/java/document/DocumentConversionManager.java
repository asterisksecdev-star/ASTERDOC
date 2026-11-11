package document;

import android.content.Context;
import java.io.File;
import java.util.Locale;

public final class DocumentConversionManager {

    private DocumentConversionManager() {
    }

    public static File convertDocumentToPdf(Context context, File sourceFile, File outputPdf) throws Exception {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist.");
        }

        File cached = DocumentRenderCache.getCachedPdfPreview(context, sourceFile, outputPdf);
        if (cached != null) {
            return cached;
        }

        if (LibreOfficeEngine.nativeRenderToPdf(sourceFile.getAbsolutePath(), outputPdf.getAbsolutePath())) {
            if (outputPdf.exists() && outputPdf.length() > 0) {
                return outputPdf;
            }
        }

        return OfficeWordRenderer.convertToPdf(sourceFile, outputPdf);
    }
}
