package document;

import android.content.Context;
import java.io.File;

public final class OfficeDocumentRenderer {

    private OfficeDocumentRenderer() {
    }

    public static File convertToPdf(Context context, File sourceDoc, File outputPdf) throws Exception {
        if (sourceDoc == null || !sourceDoc.exists() || sourceDoc.length() == 0) {
            throw new IllegalArgumentException("Document does not exist or is empty.");
        }
        return DocumentConversionManager.convertDocumentToPdf(context, sourceDoc, outputPdf);
    }
}
