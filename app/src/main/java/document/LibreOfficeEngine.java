package document;

import android.util.Log;

public final class LibreOfficeEngine {

    private static boolean isNativeInitialized = false;

    private LibreOfficeEngine() {
    }

    public static synchronized boolean initializeEngine() {
        if (isNativeInitialized) return true;
        try {
            System.loadLibrary("office_renderer_jni");
            isNativeInitialized = true;
            Log.i("LibreOfficeEngine", "Native LibreOfficeKit JNI initialized successfully.");
        } catch (Throwable t) {
            Log.w("LibreOfficeEngine", "Native LibreOfficeKit shared library not bundled in APK build. Using high-fidelity offline Java Office renderer fallback.", t);
            isNativeInitialized = false;
        }
        return isNativeInitialized;
    }

    public static boolean nativeRenderToPdf(String inputPath, String outputPath) {
        if (!initializeEngine()) return false;
        try {
            return nativeConvertToPdf(inputPath, outputPath);
        } catch (Throwable t) {
            Log.e("LibreOfficeEngine", "Native render failed", t);
            return false;
        }
    }

    private static native boolean nativeConvertToPdf(String inputPath, String outputPath);
}
