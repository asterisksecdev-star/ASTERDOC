package document;

import android.content.Context;
import android.util.Log;
import java.io.File;

public final class DocumentRenderCache {

    private DocumentRenderCache() {
    }

    public static File getCachedPdfPreview(Context context, File sourceFile, File outputPdf) {
        if (outputPdf.exists() && outputPdf.length() > 0) {
            if (outputPdf.lastModified() >= sourceFile.lastModified()) {
                return outputPdf;
            } else {
                boolean deleted = outputPdf.delete();
                Log.d("DocumentRenderCache", "Invalidated stale preview cache: " + deleted);
            }
        }
        return null;
    }
}
