package com.wrist.deepseek.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

/** Draws a compact vertical fraction (numerator, rule, denominator). */
public class FractionSpan extends ReplacementSpan {
    private final String numerator;
    private final String denominator;
    private final float pad;
    private final float gap;
    private final int color;

    public FractionSpan(String numerator, String denominator, float pad, float gap) {
        this(numerator, denominator, pad, gap, 0);
    }

    public FractionSpan(String numerator, String denominator, float pad, float gap,
                        int color) {
        this.numerator = numerator == null ? "" : numerator;
        this.denominator = denominator == null ? "" : denominator;
        this.pad = pad;
        this.gap = gap;
        this.color = color;
    }

    private TextPaint small(Paint paint) {
        TextPaint tp = new TextPaint(paint);
        // Keep numerator/denominator at the surrounding formula size.
        tp.setTextSize(paint.getTextSize());
        tp.setStyle(Paint.Style.FILL);
        if (color != 0) tp.setColor(color);
        return tp;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        TextPaint tp = small(paint);
        float w = Math.max(tp.measureText(numerator), tp.measureText(denominator));
        float line = tp.getFontMetrics().descent - tp.getFontMetrics().ascent;
        float total = line * 2 + gap + pad * 2;
        if (fm != null) {
            fm.ascent = -(int) Math.ceil(total);
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return (int) Math.ceil(w + pad * 2);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        TextPaint tp = small(paint);
        Paint.FontMetrics f = tp.getFontMetrics();
        float lineH = f.descent - f.ascent;
        float total = lineH * 2 + gap + pad * 2;
        float w = Math.max(tp.measureText(numerator), tp.measureText(denominator)) + pad * 2;
        float topY = y - total;
        float numBase = topY + pad - f.ascent;
        float denBase = topY + pad + lineH + gap - f.ascent;
        float lineY = topY + pad + lineH + gap / 2f;

        canvas.drawText(numerator, x + (w - tp.measureText(numerator)) / 2f,
                numBase, tp);
        canvas.drawText(denominator, x + (w - tp.measureText(denominator)) / 2f,
                denBase, tp);

        Paint linePaint = new Paint(tp);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(tp.getColor());
        linePaint.setStrokeWidth(Math.max(1.2f, tp.getTextSize() * 0.09f));
        canvas.drawLine(x + pad * 0.5f, lineY, x + w - pad * 0.5f, lineY, linePaint);
    }
}
