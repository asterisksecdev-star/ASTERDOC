package com.asterdoc.app.ui.viewer;

public class FocalPointMath {

    /**
     * Calculates new translation to keep the pinch focal point stationary during zooming.
     * Formula: t1 = f - (f - t0) * (z1 / z0)
     *
     * @param f  focal point in screen coordinates (X or Y)
     * @param t0 current translation (X or Y)
     * @param z0 old scale factor
     * @param z1 new scale factor
     * @return new translation (X or Y)
     */
    public static float calculateNewTranslation(float f, float t0, float z0, float z1) {
        if (z0 <= 0f) return t0;
        float scaleRatio = z1 / z0;
        return f - (f - t0) * scaleRatio;
    }

    /**
     * Clamps translation so content remains properly bounded within the viewport.
     *
     * @param trans current translation
     * @param viewSize size of the viewport (width or height)
     * @param contentSize size of the content at the current scale
     * @return corrected translation
     */
    public static float clampTranslation(float trans, float viewSize, float contentSize) {
        float minTrans;
        float maxTrans;

        if (contentSize <= viewSize) {
            // Center the content if it fits entirely in view
            return (viewSize - contentSize) / 2f;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0f;
            if (trans < minTrans) return minTrans;
            if (trans > maxTrans) return maxTrans;
            return trans;
        }
    }
}
