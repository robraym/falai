package br.com.falai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatImageView;

/** Image view that keeps a fitted image on screen while allowing pinch zoom and panning. */
final class ZoomImageView extends AppCompatImageView {
    private final Matrix transform = new Matrix();
    private final float[] values = new float[9];
    private final PointF lastTouch = new PointF();
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private float minimumScale = 1f;
    private boolean dragging;

    ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        setBackgroundColor(android.graphics.Color.BLACK);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (getDrawable() == null) return false;
                float next = scale() * detector.getScaleFactor();
                next = Math.max(minimumScale, Math.min(minimumScale * 4f, next));
                transform.postScale(next / scale(), next / scale(), detector.getFocusX(), detector.getFocusY());
                constrain(); applyTransform(); return true;
            }
        });
    }

    @Override public void setImageBitmap(Bitmap value) {
        bitmap = value;
        super.setImageBitmap(value);
        post(this::fitToScreen);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        post(this::fitToScreen);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY()); dragging = true; break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && scale() > minimumScale) {
                    transform.postTranslate(event.getX() - lastTouch.x, event.getY() - lastTouch.y);
                    constrain(); applyTransform();
                }
                lastTouch.set(event.getX(), event.getY()); break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false; performClick(); break;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    void releaseBitmap() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
    }

    private void fitToScreen() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float imageWidth = getDrawable().getIntrinsicWidth(), imageHeight = getDrawable().getIntrinsicHeight();
        if (imageWidth <= 0 || imageHeight <= 0) return;
        minimumScale = Math.min(getWidth() / imageWidth, getHeight() / imageHeight);
        transform.reset();
        transform.postScale(minimumScale, minimumScale);
        transform.postTranslate((getWidth() - imageWidth * minimumScale) / 2f, (getHeight() - imageHeight * minimumScale) / 2f);
        applyTransform();
    }

    private float scale() { transform.getValues(values); return values[Matrix.MSCALE_X]; }

    private void constrain() {
        if (getDrawable() == null) return;
        transform.getValues(values);
        float scaledWidth = getDrawable().getIntrinsicWidth() * values[Matrix.MSCALE_X];
        float scaledHeight = getDrawable().getIntrinsicHeight() * values[Matrix.MSCALE_Y];
        float x = values[Matrix.MTRANS_X], y = values[Matrix.MTRANS_Y];
        float minX = scaledWidth <= getWidth() ? (getWidth() - scaledWidth) / 2f : getWidth() - scaledWidth;
        float maxX = scaledWidth <= getWidth() ? minX : 0f;
        float minY = scaledHeight <= getHeight() ? (getHeight() - scaledHeight) / 2f : getHeight() - scaledHeight;
        float maxY = scaledHeight <= getHeight() ? minY : 0f;
        transform.postTranslate(Math.max(minX, Math.min(maxX, x)) - x, Math.max(minY, Math.min(maxY, y)) - y);
    }

    private void applyTransform() { setImageMatrix(transform); }
}
