package com.wrist.deepseek.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.wrist.deepseek.util.Ui;

/** Tiny hand-drawn line icons so the APK stays small and crisp on a watch. */
public class IconView extends View {
    public static final int MENU = 1;
    public static final int PLUS = 2;
    public static final int MIC = 3;
    public static final int SEND = 4;
    public static final int BACK = 5;
    public static final int CLOSE = 6;
    public static final int MORE = 7;
    public static final int SPEAKER = 8;
    public static final int COPY = 9;
    public static final int TRASH = 10;
    public static final int STOP = 11;
    public static final int CHEVRON_DOWN = 12;
    public static final int CHEVRON_RIGHT = 13;
    public static final int SETTINGS = 14;
    public static final int HISTORY = 15;
    public static final int CHECK = 16;
    public static final int PHONE = 17;
    public static final int REFRESH = 18;
    public static final int EDIT = 19;
    public static final int SPARK = 20;
    public static final int ARROW_DOWN = 21;
    public static final int GLOBE = 22;
    public static final int LOCK = 23;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int icon;
    private int color;
    private float strokeDp = 1.9f;

    public IconView(Context c, int icon, int color, float sizeDp) {
        super(c);
        this.icon = icon;
        this.color = color;
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        int size = Ui.dp(c, sizeDp);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(size, size));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setStrokeDp(float value) {
        strokeDp = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = getLayoutParams() != null ? getLayoutParams().width : Ui.dp(getContext(), 40);
        int h = getLayoutParams() != null ? getLayoutParams().height : Ui.dp(getContext(), 40);
        if (w <= 0) w = Ui.dp(getContext(), 40);
        if (h <= 0) h = Ui.dp(getContext(), 40);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        float unit = size / 24f;
        float sw = Math.max(1f, strokeDp * unit);
        p.setColor(color);
        p.setStrokeWidth(sw);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float ox = (w - size) / 2f;
        float oy = (h - size) / 2f;
        canvas.save();
        canvas.translate(ox, oy);
        switch (icon) {
            case MENU:
                p.setStrokeWidth(sw * 1.28f);
                line(canvas, 5, 6.5f, 19, 6.5f);
                line(canvas, 5, 12, 19, 12);
                line(canvas, 5, 17.5f, 19, 17.5f);
                break;
            case PLUS:
                line(canvas, 12, 5, 12, 19);
                line(canvas, 5, 12, 19, 12);
                break;
            case MIC:
                p.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(new RectF(9 * unit, 3 * unit, 15 * unit, 14 * unit),
                        3 * unit, 3 * unit, p);
                canvas.drawArc(new RectF(5.5f * unit, 7 * unit, 18.5f * unit, 18 * unit),
                        0, 180, false, p);
                line(canvas, 12, 18, 12, 21);
                line(canvas, 8.5f, 21, 15.5f, 21);
                break;
            case SEND:
                p.setStyle(Paint.Style.STROKE);
                Path send = new Path();
                send.moveTo(4 * unit, 11.5f * unit);
                send.lineTo(20 * unit, 4 * unit);
                send.lineTo(13.5f * unit, 20 * unit);
                send.lineTo(10.5f * unit, 13.5f * unit);
                send.close();
                canvas.drawPath(send, p);
                line(canvas, 10.5f, 13.5f, 20, 4);
                break;
            case BACK:
                Path back = new Path();
                back.moveTo(14.5f * unit, 5 * unit);
                back.lineTo(7.5f * unit, 12 * unit);
                back.lineTo(14.5f * unit, 19 * unit);
                canvas.drawPath(back, p);
                break;
            case CLOSE:
                line(canvas, 6, 6, 18, 18);
                line(canvas, 18, 6, 6, 18);
                break;
            case MORE:
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(6 * unit, 12 * unit, 1.5f * unit, p);
                canvas.drawCircle(12 * unit, 12 * unit, 1.5f * unit, p);
                canvas.drawCircle(18 * unit, 12 * unit, 1.5f * unit, p);
                break;
            case SPEAKER:
                p.setStyle(Paint.Style.STROKE);
                Path sp = new Path();
                sp.moveTo(4 * unit, 9 * unit);
                sp.lineTo(8 * unit, 9 * unit);
                sp.lineTo(13 * unit, 5 * unit);
                sp.lineTo(13 * unit, 19 * unit);
                sp.lineTo(8 * unit, 15 * unit);
                sp.lineTo(4 * unit, 15 * unit);
                sp.close();
                canvas.drawPath(sp, p);
                canvas.drawArc(new RectF(13 * unit, 7 * unit, 21 * unit, 17 * unit), -55, 110, false, p);
                break;
            case COPY:
                canvas.drawRoundRect(new RectF(8 * unit, 8 * unit, 20 * unit, 20 * unit),
                        2.5f * unit, 2.5f * unit, p);
                Path copy = new Path();
                copy.moveTo(5 * unit, 16 * unit);
                copy.lineTo(4 * unit, 16 * unit);
                copy.lineTo(4 * unit, 4 * unit);
                copy.lineTo(16 * unit, 4 * unit);
                copy.lineTo(16 * unit, 5 * unit);
                canvas.drawPath(copy, p);
                break;
            case TRASH:
                line(canvas, 4, 7, 20, 7);
                line(canvas, 9, 7, 9.5f, 4);
                line(canvas, 15, 7, 14.5f, 4);
                line(canvas, 9.5f, 4, 14.5f, 4);
                Path can = new Path();
                can.moveTo(6 * unit, 7 * unit);
                can.lineTo(7 * unit, 20 * unit);
                can.lineTo(17 * unit, 20 * unit);
                can.lineTo(18 * unit, 7 * unit);
                canvas.drawPath(can, p);
                line(canvas, 10, 11, 10.5f, 16);
                line(canvas, 14, 11, 13.5f, 16);
                break;
            case STOP:
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(7 * unit, 7 * unit, 17 * unit, 17 * unit),
                        2.5f * unit, 2.5f * unit, p);
                break;
            case CHEVRON_DOWN:
                Path cd = new Path();
                cd.moveTo(6 * unit, 9 * unit);
                cd.lineTo(12 * unit, 15 * unit);
                cd.lineTo(18 * unit, 9 * unit);
                canvas.drawPath(cd, p);
                break;
            case CHEVRON_RIGHT:
                Path cr = new Path();
                cr.moveTo(9 * unit, 6 * unit);
                cr.lineTo(15 * unit, 12 * unit);
                cr.lineTo(9 * unit, 18 * unit);
                canvas.drawPath(cr, p);
                break;
            case SETTINGS:
                line(canvas, 4, 7, 20, 7);
                line(canvas, 4, 17, 20, 17);
                line(canvas, 4, 12, 20, 12);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(9 * unit, 7 * unit, 2.4f * unit, p);
                canvas.drawCircle(15 * unit, 12 * unit, 2.4f * unit, p);
                canvas.drawCircle(8 * unit, 17 * unit, 2.4f * unit, p);
                break;
            case HISTORY:
                canvas.drawCircle(12 * unit, 12 * unit, 8.5f * unit, p);
                line(canvas, 12, 7, 12, 12.5f);
                line(canvas, 12, 12.5f, 16, 14.5f);
                break;
            case CHECK:
                Path check = new Path();
                check.moveTo(5 * unit, 12.5f * unit);
                check.lineTo(10 * unit, 17 * unit);
                check.lineTo(19 * unit, 7 * unit);
                canvas.drawPath(check, p);
                break;
            case PHONE:
                canvas.drawRoundRect(new RectF(6 * unit, 2.5f * unit, 18 * unit, 21.5f * unit),
                        3 * unit, 3 * unit, p);
                line(canvas, 10.5f, 18.5f, 13.5f, 18.5f);
                break;
            case REFRESH:
                canvas.drawArc(new RectF(4 * unit, 4 * unit, 20 * unit, 20 * unit), -40, 290, false, p);
                Path ra = new Path();
                ra.moveTo(17 * unit, 3 * unit);
                ra.lineTo(21 * unit, 6 * unit);
                ra.lineTo(17 * unit, 9 * unit);
                canvas.drawPath(ra, p);
                break;
            case EDIT:
                Path pen = new Path();
                pen.moveTo(5 * unit, 19 * unit);
                pen.lineTo(6 * unit, 14 * unit);
                pen.lineTo(16 * unit, 4 * unit);
                pen.lineTo(20 * unit, 8 * unit);
                pen.lineTo(10 * unit, 18 * unit);
                pen.close();
                canvas.drawPath(pen, p);
                line(canvas, 14.5f, 5.5f, 18.5f, 9.5f);
                break;
            case SPARK:
                p.setStyle(Paint.Style.FILL);
                Path star = new Path();
                star.moveTo(12 * unit, 2 * unit);
                star.quadTo(13.5f * unit, 9 * unit, 22 * unit, 12 * unit);
                star.quadTo(13.5f * unit, 15 * unit, 12 * unit, 22 * unit);
                star.quadTo(10.5f * unit, 15 * unit, 2 * unit, 12 * unit);
                star.quadTo(10.5f * unit, 9 * unit, 12 * unit, 2 * unit);
                star.close();
                canvas.drawPath(star, p);
                break;
            case ARROW_DOWN:
                line(canvas, 12, 4, 12, 20);
                Path ad = new Path();
                ad.moveTo(6 * unit, 14 * unit);
                ad.lineTo(12 * unit, 20 * unit);
                ad.lineTo(18 * unit, 14 * unit);
                canvas.drawPath(ad, p);
                break;
            case GLOBE:
                canvas.drawCircle(12 * unit, 12 * unit, 8.5f * unit, p);
                // Exactly two meridians (the oval's two sides) ...
                canvas.drawOval(new RectF(8 * unit, 3.5f * unit, 16 * unit, 20.5f * unit), p);
                // ... and exactly two latitude lines through the middle.
                line(canvas, 4.4f, 9, 19.6f, 9);
                line(canvas, 4.4f, 15, 19.6f, 15);
                break;
            case LOCK:
                canvas.drawArc(new RectF(7 * unit, 3 * unit, 17 * unit, 15 * unit),
                        180, 180, false, p);
                canvas.drawRoundRect(new RectF(5 * unit, 10 * unit, 19 * unit, 21 * unit),
                        3 * unit, 3 * unit, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12 * unit, 15 * unit, 1.2f * unit, p);
                break;
            default:
                canvas.drawCircle(12 * unit, 12 * unit, 8 * unit, p);
                break;
        }
        canvas.restore();
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        float unit = Math.min(getWidth(), getHeight()) / 24f;
        c.drawLine(x1 * unit, y1 * unit, x2 * unit, y2 * unit, p);
    }
}
