package document;

import android.content.Context;
import java.io.File;

public final class WordRenderingEngine {

    private WordRenderingEngine() {
    }

    public static File renderWordToPdf(Context context, File sourceWord, File outputPdf) throws Exception {
        if (sourceWord == null || !sourceWord.exists() || sourceWord.length() == 0) {
            throw new IllegalArgumentException("Word document does not exist or is empty.");
        }
        return DocumentConversionManager.convertDocumentToPdf(context, sourceWord, outputPdf);
    }
}
