package com.asterdoc.app.ui.viewer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView implements
        ScaleGestureDetector.OnScaleGestureListener,
        GestureDetector.OnDoubleTapListener {

    public interface OnZoomImageClickListener {
        void onSingleTap();
    }

    public interface OnMatrixChangedListener {
        void onMatrixChanged(Matrix matrix, float saveScale);
    }

    private Matrix matrix = new Matrix();
    private int mode = 0; // 0 = NONE, 1 = DRAG, 2 = ZOOM
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    private final PointF lastPoint = new PointF();
    private final PointF startPoint = new PointF();
    private final float minScale = 1.0f;
    private final float maxScale = 5.0f;
    private final float[] m = new float[9];

    private int viewWidth, viewHeight;
    private float saveScale = 1.0f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnZoomImageClickListener tapListener;
    private OnMatrixChangedListener matrixChangedListener;
    private ValueAnimator animator;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public interface OnZoomScaleChangeListener {
        void onZoomScaleChanged(float newScale);
    }

    private OnZoomScaleChangeListener zoomScaleChangeListener;

    public void setOnZoomScaleChangeListener(OnZoomScaleChangeListener listener) {
        this.zoomScaleChangeListener = listener;
    }

    public void setSaveScale(float scale) {
        if (this.saveScale != scale) {
            this.saveScale = Math.max(minScale, Math.min(maxScale, scale));
            requestLayout();
            if (zoomScaleChangeListener != null) {
                zoomScaleChangeListener.onZoomScaleChanged(this.saveScale);
            }
        }
    }

    public void setSaveScaleQuiet(float scale) {
        if (this.saveScale != scale) {
            this.saveScale = Math.max(minScale, Math.min(maxScale, scale));
            requestLayout();
        }
    }

    public void setOnZoomImageClickListener(OnZoomImageClickListener listener) {
        this.tapListener = listener;
    }

    public void setOnMatrixChangedListener(OnMatrixChangedListener listener) {
        this.matrixChangedListener = listener;
    }

    private void init(Context context) {
        super.setClickable(true);
        scaleDetector = new ScaleGestureDetector(context, this);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener());
        gestureDetector.setOnDoubleTapListener(this);
        matrix = new Matrix();
        setScaleType(ScaleType.MATRIX);
        updateMatrix();
    }

    private void updateMatrix() {
        setImageMatrix(matrix);
        if (matrixChangedListener != null) {
            matrixChangedListener.onMatrixChanged(matrix, saveScale);
        }
    }

    private void updateSymmetricExpansion() {
        setTranslationY(0f);
    }

    private float getMatrixTranslationX() {
        matrix.getValues(m);
        return m[Matrix.MTRANS_X];
    }

    private float getMatrixTranslationY() {
        matrix.getValues(m);
        return m[Matrix.MTRANS_Y];
    }

    public float getSaveScale() {
        return saveScale;
    }

    public void animateScaleTo(float targetScale, float focusX, float focusY) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        final float startScale = saveScale;
        final float startTransX = getMatrixTranslationX();
        final float startTransY = getMatrixTranslationY();

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float currentScaleFactor = startScale + (targetScale - startScale) * fraction;
            saveScale = currentScaleFactor;

            requestLayout();

            float baseScale = (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0)
                    ? (float) viewWidth / getDrawable().getIntrinsicWidth() : 1f;
            float currentScale = baseScale * saveScale;

            float pageH = (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0)
                    ? viewWidth * ((float) getDrawable().getIntrinsicHeight() / getDrawable().getIntrinsicWidth()) : viewHeight;
            float contentWidth = viewWidth * saveScale;
            float contentHeight = pageH * saveScale;

            float newTransX = FocalPointMath.calculateNewTranslation(focusX, startTransX, startScale, saveScale);
            float newTransY = FocalPointMath.calculateNewTranslation(focusY, startTransY, startScale, saveScale);

            newTransX = FocalPointMath.clampTranslation(newTransX, viewWidth, contentWidth);
            newTransY = FocalPointMath.clampTranslation(newTransY, pageH, contentHeight);

            matrix.reset();
            matrix.setScale(currentScale, currentScale);
            matrix.postTranslate(newTransX, newTransY);

            updateMatrix();
            invalidate();

            if (zoomScaleChangeListener != null) {
                zoomScaleChangeListener.onZoomScaleChanged(saveScale);
            }
        });

        animator.start();
    }

    public void zoomIn() {
        float targetScale = Math.min(maxScale, saveScale * 1.4f);
        animateScaleTo(targetScale, viewWidth / 2f, viewHeight / 2f);
    }

    public void zoomOut() {
        float targetScale = Math.max(minScale, saveScale / 1.4f);
        animateScaleTo(targetScale, viewWidth / 2f, viewHeight / 2f);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        performClick();
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        PointF currentPoint = new PointF(event.getX(), event.getY());

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastPoint.set(currentPoint);
                startPoint.set(lastPoint);
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && saveScale > 1.0f) {
                    float deltaX = currentPoint.x - lastPoint.x;
                    float deltaY = currentPoint.y - lastPoint.y;

                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 5) {
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }

                        float baseScale = (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0)
                                ? (float) viewWidth / getDrawable().getIntrinsicWidth() : 1f;
                        float currentScale = baseScale * saveScale;

                        float currentTransX = getMatrixTranslationX();
                        float contentWidth = viewWidth * saveScale;
                        float newTransX = FocalPointMath.clampTranslation(currentTransX + deltaX, viewWidth, contentWidth);

                        matrix.reset();
                        matrix.setScale(currentScale, currentScale);
                        matrix.postTranslate(newTransX, 0f);
                        updateMatrix();
                        invalidate();
                    } else if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    lastPoint.set(currentPoint.x, currentPoint.y);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }

        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        float scaleFactor = detector.getScaleFactor();
        float oldScale = saveScale;
        float newScale = oldScale * scaleFactor;

        if (newScale > maxScale) {
            newScale = maxScale;
        } else if (newScale < minScale) {
            newScale = minScale;
        }

        if (oldScale != newScale) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float currentTransX = getMatrixTranslationX();
            float currentTransY = getMatrixTranslationY();

            saveScale = newScale;
            requestLayout();

            float baseScale = (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0)
                    ? (float) viewWidth / getDrawable().getIntrinsicWidth() : 1f;
            float currentScale = baseScale * saveScale;

            float contentWidth = viewWidth * saveScale;
            float pageH = (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0)
                    ? viewWidth * ((float) getDrawable().getIntrinsicHeight() / getDrawable().getIntrinsicWidth()) : viewHeight;
            float contentHeight = pageH * saveScale;

            float newTransX = FocalPointMath.calculateNewTranslation(focusX, currentTransX, oldScale, newScale);
            float newTransY = FocalPointMath.calculateNewTranslation(focusY, currentTransY, oldScale, newScale);

            newTransX = FocalPointMath.clampTranslation(newTransX, viewWidth, contentWidth);
            newTransY = FocalPointMath.clampTranslation(newTransY, pageH, contentHeight);

            matrix.reset();
            matrix.setScale(currentScale, currentScale);
            matrix.postTranslate(newTransX, newTransY);
            updateMatrix();

            if (zoomScaleChangeListener != null) {
                zoomScaleChangeListener.onZoomScaleChanged(saveScale);
            }
        }

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mode = ZOOM;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (saveScale <= 1.0f && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getDrawable() != null && getDrawable().getIntrinsicWidth() > 0 && getDrawable().getIntrinsicHeight() > 0) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int pageH = (int) ((float) w * getDrawable().getIntrinsicHeight() / getDrawable().getIntrinsicWidth());
            viewWidth = w;
            int zoomedH = (int) (pageH * saveScale);
            viewHeight = zoomedH;

            setMeasuredDimension(w, zoomedH);
            updateSymmetricExpansion();

            float baseScale = (float) w / getDrawable().getIntrinsicWidth();
            float currentScale = baseScale * saveScale;

            float contentWidth = w * saveScale;
            float contentHeight = pageH * saveScale;

            float currentTransX = getMatrixTranslationX();
            float currentTransY = getMatrixTranslationY();

            currentTransX = FocalPointMath.clampTranslation(currentTransX, w, contentWidth);
            currentTransY = FocalPointMath.clampTranslation(currentTransY, pageH, contentHeight);

            matrix.reset();
            matrix.setScale(currentScale, currentScale);
            matrix.postTranslate(currentTransX, currentTransY);
            updateMatrix();
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (tapListener != null) {
            tapListener.onSingleTap();
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        float targetScale = (saveScale == minScale) ? 2.5f : minScale;
        animateScaleTo(targetScale, e.getX(), e.getY());
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }
}

