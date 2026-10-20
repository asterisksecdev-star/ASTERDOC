package com.asterdoc.app.ui.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DrawingCanvasView extends View {

    public enum ToolMode {
        NONE, PEN, HIGHLIGHTER, ERASER
    }

    public static class DrawPath {
        public Path path;
        public Paint paint;

        public DrawPath(Path path, Paint paint) {
            this.path = path;
            this.paint = paint;
        }
    }

    private final List<DrawPath> paths = new ArrayList<>();
    private final List<DrawPath> undoHistory = new ArrayList<>();
    private final Matrix drawMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();

    private Path currentPath;
    private Paint currentPaint;

    private ToolMode currentTool = ToolMode.NONE;
    private int currentColor = Color.RED;
    private float currentStrokeWidth = 8f;

    private float lastTouchX, lastTouchY;
    private String searchQuery = null;

    public void setSearchQuery(String query) {
        this.searchQuery = query;
        invalidate();
    }

    public DrawingCanvasView(Context context) {
        super(context);
        init();
    }

    public DrawingCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setupPaint();
    }

    public void setDrawMatrix(Matrix matrix) {
        if (matrix != null) {
            this.drawMatrix.set(matrix);
            this.drawMatrix.invert(inverseMatrix);
            invalidate();
        }
    }

    private void setupPaint() {
        currentPaint = new Paint();
        currentPaint.setAntiAlias(true);
        currentPaint.setDither(true);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeJoin(Paint.Join.ROUND);
        currentPaint.setStrokeCap(Paint.Cap.ROUND);

        updateToolPaint();
    }

    public void setToolMode(ToolMode mode) {
        this.currentTool = mode;
        updateToolPaint();
        invalidate();
    }

    public ToolMode getToolMode() {
        return currentTool;
    }

    public void setColor(int color) {
        this.currentColor = color;
        updateToolPaint();
    }

    public void setStrokeWidth(float width) {
        this.currentStrokeWidth = width;
        updateToolPaint();
    }

    private void updateToolPaint() {
        if (currentTool == ToolMode.PEN) {
            currentPaint.setColor(currentColor);
            currentPaint.setAlpha(255);
            currentPaint.setStrokeWidth(currentStrokeWidth);
            currentPaint.setXfermode(null);
        } else if (currentTool == ToolMode.HIGHLIGHTER) {
            currentPaint.setColor(currentColor);
            currentPaint.setAlpha(100);
            currentPaint.setStrokeWidth(currentStrokeWidth * 2.5f);
            currentPaint.setXfermode(null);
        } else if (currentTool == ToolMode.ERASER) {
            currentPaint.setColor(Color.TRANSPARENT);
            currentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            currentPaint.setStrokeWidth(currentStrokeWidth * 3f);
        }
    }

    public void undo() {
        if (!paths.isEmpty()) {
            DrawPath removed = paths.remove(paths.size() - 1);
            undoHistory.add(removed);
            invalidate();
        }
    }

    public void redo() {
        if (!undoHistory.isEmpty()) {
            DrawPath restored = undoHistory.remove(undoHistory.size() - 1);
            paths.add(restored);
            invalidate();
        }
    }

    public void clear() {
        paths.clear();
        undoHistory.clear();
        invalidate();
    }

    public boolean hasDrawings() {
        return !paths.isEmpty();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.concat(drawMatrix);
        for (DrawPath dp : paths) {
            canvas.drawPath(dp.path, dp.paint);
        }
        if (currentPath != null && currentPaint != null && currentTool != ToolMode.NONE) {
            canvas.drawPath(currentPath, currentPaint);
        }
        canvas.restore();

        // Draw professional search highlight banner overlay if active
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            Paint highlightPaint = new Paint();
            highlightPaint.setColor(Color.parseColor("#FFF59D")); // Light yellow highlight
            highlightPaint.setStyle(Paint.Style.FILL);
            highlightPaint.setAlpha(200);

            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.parseColor("#FBC02D")); // Amber border
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4f);
            borderPaint.setAntiAlias(true);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.parseColor("#1F1F1F"));
            textPaint.setTextSize(34f);
            textPaint.setFakeBoldText(true);
            textPaint.setAntiAlias(true);

            float boxLeft = getWidth() * 0.08f;
            float boxTop = getHeight() * 0.20f;
            float boxRight = getWidth() * 0.92f;
            float boxBottom = boxTop + 110f;

            canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, highlightPaint);
            canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, borderPaint);

            canvas.drawText("Match: \"" + searchQuery + "\"", boxLeft + 28f, boxTop + 68f, textPaint);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentTool == ToolMode.NONE) {
            return false;
        }
        performClick();

        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        float[] pts = new float[]{event.getX(), event.getY()};
        inverseMatrix.mapPoints(pts);
        float x = pts[0];
        float y = pts[1];

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                undoHistory.clear();
                currentPath = new Path();
                currentPath.moveTo(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(x - lastTouchX);
                float dy = Math.abs(y - lastTouchY);
                if (dx >= 3 || dy >= 3) {
                    currentPath.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2);
                    lastTouchX = x;
                    lastTouchY = y;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    Paint savePaint = new Paint(currentPaint);
                    paths.add(new DrawPath(currentPath, savePaint));
                    currentPath = null;
                }
                invalidate();
                return true;

            default:
                return false;
        }
    }

    public void drawOntoBitmap(Bitmap targetBitmap) {
        if (targetBitmap == null || paths.isEmpty()) return;
        Canvas canvas = new Canvas(targetBitmap);
        float scaleX = (float) targetBitmap.getWidth() / getWidth();
        float scaleY = (float) targetBitmap.getHeight() / getHeight();

        canvas.save();
        canvas.scale(scaleX, scaleY);
        for (DrawPath dp : paths) {
            canvas.drawPath(dp.path, dp.paint);
        }
        canvas.restore();
    }
}
