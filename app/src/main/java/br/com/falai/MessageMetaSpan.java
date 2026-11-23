package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;

/** An inline footer: wraps naturally with the text instead of forcing a separate row. */
final class MessageMetaSpan extends ReplacementSpan {
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG), checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String time;
    private final float density, width;
    private final int status;
    private final Path first = new Path(), second = new Path();
    MessageMetaSpan(Context context, String time, int status) {
        this.time = time; this.status = status; density = context.getResources().getDisplayMetrics().density;
        timePaint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 11, context.getResources().getDisplayMetrics()));
        timePaint.setColor(context.getColor(R.color.text_secondary));
        checkPaint.setColor(context.getColor(status == 3 ? R.color.check_read : R.color.check_pending)); checkPaint.setStyle(Paint.Style.STROKE); checkPaint.setStrokeWidth(1.5f); checkPaint.setStrokeCap(Paint.Cap.ROUND); checkPaint.setStrokeJoin(Paint.Join.ROUND);
        first.moveTo(1, 6); first.lineTo(5, 10); first.lineTo(14, 1); second.moveTo(11, 10); second.lineTo(20, 1);
        width = timePaint.measureText(time) + (status > 0 ? 25 * density : 0) + 6 * density;
    }
    @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) { return (int) Math.ceil(width); }
    @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.drawText(time, x + 4 * density, y, timePaint);
        if (status > 0) {
            canvas.save(); canvas.translate(x + timePaint.measureText(time) + 8 * density, y - 9 * density); canvas.scale(density, density);
            canvas.drawPath(first, checkPaint); if (status > 1) canvas.drawPath(second, checkPaint); canvas.restore();
        }
    }
}
