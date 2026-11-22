package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Drawn strokes keep check shape/color consistent across Android emoji fonts. */
final class MessageChecks extends View {
    private final boolean two;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path first = new Path(), second = new Path();
    public MessageChecks(Context context) { this(context, false, false); }
    MessageChecks(Context context, boolean delivered, boolean read) {
        super(context); two = delivered || read;
        paint.setColor(context.getColor(read ? R.color.check_read : R.color.check_pending));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(context, 1.6f));
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription(context.getString(read ? R.string.message_read : delivered ? R.string.message_delivered : R.string.message_sent));
        first.moveTo(2, 8); first.lineTo(6, 12); first.lineTo(16, 2);
        second.moveTo(12, 12); second.lineTo(22, 2);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.translate((getWidth() - Ui.dp(getContext(), 24)) / 2f, (getHeight() - Ui.dp(getContext(), 16)) / 2f);
        canvas.scale(getResources().getDisplayMetrics().density, getResources().getDisplayMetrics().density);
        canvas.drawPath(first, paint);
        if (two) canvas.drawPath(second, paint);
        canvas.restore();
    }
}
