package com.asterdoc.app.ui.viewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FocalPointMathTest {

    private static final float DELTA = 0.001f;

    @Test
    public void testZoomNearCenter() {
        // Center focal point at 500, current translation 0, scale from 1.0 to 2.0
        float focalX = 500f;
        float t0 = 0f;
        float z0 = 1.0f;
        float z1 = 2.0f;

        float t1 = FocalPointMath.calculateNewTranslation(focalX, t0, z0, z1);
        // t1 = 500 - (500 - 0) * (2.0 / 1.0) = 500 - 1000 = -500
        assertEquals(-500f, t1, DELTA);
    }

    @Test
    public void testZoomAtOrigin() {
        // Focal point at origin 0,0
        float focalX = 0f;
        float t0 = 0f;
        float z0 = 1.0f;
        float z1 = 2.5f;

        float t1 = FocalPointMath.calculateNewTranslation(focalX, t0, z0, z1);
        // t1 = 0 - (0 - 0) * 2.5 = 0
        assertEquals(0f, t1, DELTA);
    }

    @Test
    public void testZoomNearPageEdge() {
        // Focal point near edge (1000), current translation -200, scale 2.0 to 3.0
        float focalX = 1000f;
        float t0 = -200f;
        float z0 = 2.0f;
        float z1 = 3.0f;

        float t1 = FocalPointMath.calculateNewTranslation(focalX, t0, z0, z1);
        // t1 = 1000 - (1000 - (-200)) * (3.0 / 2.0) = 1000 - 1200 * 1.5 = 1000 - 1800 = -800
        assertEquals(-800f, t1, DELTA);
    }

    @Test
    public void testClampTranslationWhenContentLargerThanView() {
        float viewSize = 1080f;
        float contentSize = 2160f;

        // Translation out of bounds to the right (+100)
        float clampedRight = FocalPointMath.clampTranslation(100f, viewSize, contentSize);
        assertEquals(0f, clampedRight, DELTA);

        // Translation out of bounds to the left (-1500)
        float clampedLeft = FocalPointMath.clampTranslation(-1500f, viewSize, contentSize);
        assertEquals(-1080f, clampedLeft, DELTA); // minTrans = 1080 - 2160 = -1080
    }

    @Test
    public void testClampTranslationWhenContentSmallerThanView() {
        float viewSize = 1080f;
        float contentSize = 800f;

        // Should center content at (1080 - 800) / 2 = 140
        float clamped = FocalPointMath.clampTranslation(0f, viewSize, contentSize);
        assertEquals(140f, clamped, DELTA);
    }
}
