package document;

import android.content.Context;
import java.io.File;

public final class WordDocumentRenderer {

    private WordDocumentRenderer() {
    }

    public static File convertToPdf(Context context, File sourceFile, File outputPdf) throws Exception {
        return WordRenderingEngine.renderWordToPdf(context, sourceFile, outputPdf);
    }
}
