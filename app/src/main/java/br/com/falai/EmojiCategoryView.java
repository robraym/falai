package br.com.falai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Monochrome category shortcuts, visually distinct from selectable emoji. */
final class EmojiCategoryView extends View {
    private final int kind;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    EmojiCategoryView(Context context, int kind) { super(context); this.kind = kind; setFocusable(true); }
    @Override public void setSelected(boolean selected) { super.setSelected(selected); invalidate(); }
    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a,b,x,y,pen); }
    private void circle(Canvas c,float x,float y,float r) { c.drawCircle(x,y,r,pen); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); float side = Ui.dp(getContext(), 25);
        c.translate((getWidth()-side)/2, (getHeight()-side)/2); c.scale(side/24,side/24);
        pen.setColor(getContext().getColor(isSelected() ? R.color.text_primary : R.color.text_secondary));
        pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(1.8f); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        switch(kind) {
            case -1: circle(c,12,12,9);line(c,12,6,12,12);line(c,12,12,16,15);break;
            case 0: circle(c,12,12,9);circle(c,8.5f,9,0.6f);circle(c,15.5f,9,.6f);c.drawArc(7,9,17,18,15,150,false,pen);break;
            case 1: circle(c,12,7,4);c.drawArc(4,13,20,27,185,170,false,pen);break;
            case 2: circle(c,5,8,2);circle(c,10,5,2);circle(c,16,6,2);circle(c,20,11,2);c.drawOval(7,12,17,21,pen);break;
            case 3: c.drawRoundRect(4,5,15,18,2,2,pen);c.drawArc(12,7,22,16,-90,180,false,pen);line(c,3,21,20,21);break;
            case 4: circle(c,12,12,9);c.drawOval(8,3,16,21,pen);line(c,3,12,21,12);break;
            case 5: circle(c,12,12,9);path.rewind();for(int i=0;i<6;i++){double a=(i%5)*Math.PI*2/5-Math.PI/2;float x=12+(float)Math.cos(a)*4,y=12+(float)Math.sin(a)*4;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,pen);for(int i=0;i<5;i++){double a=i*Math.PI*2/5-Math.PI/2;line(c,12+(float)Math.cos(a)*4,12+(float)Math.sin(a)*4,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9);}break;
            case 6: c.drawArc(5,3,19,17,145,250,false,pen);line(c,7,15,9,19);line(c,17,15,15,19);line(c,9,19,15,19);line(c,10,22,14,22);break;
            case 7: circle(c,7,7,3);circle(c,17,17,3);line(c,6,20,18,4);break;
            case 8: line(c,5,22,5,3);path.rewind();path.moveTo(5,4);path.lineTo(20,4);path.lineTo(17,9);path.lineTo(20,14);path.lineTo(5,14);c.drawPath(path,pen);break;
            case 9: path.rewind();path.moveTo(12,21);path.cubicTo(9,18,2,13,2,8);path.cubicTo(2,2,9,1,12,6);path.cubicTo(15,1,22,2,22,8);path.cubicTo(22,13,15,18,12,21);c.drawPath(path,pen);break;
            case 10: path.rewind();path.moveTo(3,21);path.lineTo(7,8);path.lineTo(16,17);path.close();c.drawPath(path,pen);line(c,12,4,15,2);line(c,19,9,22,7);line(c,17,5,18,2);circle(c,21,15,1);break;
            case 11: path.rewind();path.moveTo(4,20);path.cubicTo(2,6,12,2,21,3);path.cubicTo(22,15,13,21,4,20);c.drawPath(path,pen);line(c,3,22,16,9);break;
            case 12: path.rewind();path.moveTo(6,19);path.lineTo(2,12);path.cubicTo(1,9,4,8,6,12);path.lineTo(6,5);path.cubicTo(6,2,9,2,9,5);path.lineTo(9,11);path.lineTo(9,3);path.cubicTo(9,0,12,0,12,3);path.lineTo(12,11);path.lineTo(12,5);path.cubicTo(12,2,15,2,15,5);path.lineTo(15,12);path.lineTo(15,8);path.cubicTo(15,5,18,5,18,8);path.lineTo(18,16);path.cubicTo(18,23,10,24,6,19);c.drawPath(path,pen);break;
        }
        c.restore();
    }
}
