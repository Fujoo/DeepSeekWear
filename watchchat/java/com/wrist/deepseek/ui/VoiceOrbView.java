package com.wrist.deepseek.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import com.wrist.deepseek.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** The listening orb: quiet, animated, and driven by real microphone energy. */
public class VoiceOrbView extends View {
    public static final int RECORDING = 0;
    public static final int THINKING = 1;
    public static final int ERROR = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> history = new ArrayList<>();
    private float level;
    private float displayLevel;
    private int state = RECORDING;
    private long lastTick;

    public VoiceOrbView(Context c, float sizeDp) {
        super(c);
        int s = Ui.dp(c, sizeDp);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(s, s));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setLevel(float value) {
        level = Math.max(0, Math.min(1, value));
        history.add(level);
        while (history.size() > 30) history.remove(0);
        invalidate();
    }

    public void setState(int state) {
        this.state = state;
        invalidate();
    }

    public void clearLevel() {
        history.clear();
        level = 0;
        displayLevel = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (lastTick == 0) lastTick = now;
        float dt = Math.min(0.05f, (now - lastTick) / 1000f);
        lastTick = now;
        displayLevel += (level - displayLevel) * Math.min(1f, dt * 10f);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.43f;
        float base = Math.min(w, h) * 0.235f;
        float t = now / 1000f;

        // Ambient glow.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, base * 1.9f,
                new int[]{0x664D6BFE, 0x224D6BFE, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, base * 1.9f, paint);
        paint.setShader(null);

        // Breathing rings.
        if (state == RECORDING) {
            for (int i = 0; i < 3; i++) {
                float phase = (t * 0.42f + i / 3f) % 1f;
                float r = base * (1.0f + phase * 0.72f + displayLevel * 0.11f);
                int alpha = (int) ((1f - phase) * (46 + displayLevel * 40));
                stroke.setColor(Color.argb(alpha, 120, 145, 255));
                stroke.setStrokeWidth(Ui.dp(getContext(), 1.1f));
                canvas.drawCircle(cx, cy, r, stroke);
            }
        }

        // Orb.
        float orb = base * (1f + displayLevel * 0.095f);
        paint.setShader(new LinearGradient(cx - orb, cy - orb, cx + orb, cy + orb,
                new int[]{0xFF7E94FF, 0xFF4D6BFE, 0xFF3047CE},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, orb, paint);
        paint.setShader(null);

        if (state == THINKING) {
            stroke.setColor(0xFFDCE3FF);
            stroke.setStrokeWidth(Ui.dp(getContext(), 2.2f));
            RectF arc = new RectF(cx - orb - Ui.dp(getContext(), 5), cy - orb - Ui.dp(getContext(), 5),
                    cx + orb + Ui.dp(getContext(), 5), cy + orb + Ui.dp(getContext(), 5));
            canvas.drawArc(arc, (now / 5f) % 360f, 74, false, stroke);
        }

        drawMic(canvas, cx, cy, orb, state == ERROR ? 0xFFFFDADA : Color.WHITE);
        if (state == RECORDING) drawWave(canvas, w, h, t);
        postInvalidateOnAnimation();
    }

    private void drawMic(Canvas canvas, float cx, float cy, float orb, int color) {
        float s = orb * 0.52f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(Ui.dp(getContext(), 1.8f), s * 0.085f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float top = cy - s * 0.62f;
        RectF cap = new RectF(cx - s * 0.25f, top, cx + s * 0.25f, cy + s * 0.15f);
        canvas.drawRoundRect(cap, s * 0.25f, s * 0.25f, paint);
        RectF arc = new RectF(cx - s * 0.42f, cy - s * 0.36f, cx + s * 0.42f, cy + s * 0.44f);
        canvas.drawArc(arc, 0, 180, false, paint);
        canvas.drawLine(cx, cy + s * 0.44f, cx, cy + s * 0.68f, paint);
        canvas.drawLine(cx - s * 0.2f, cy + s * 0.68f, cx + s * 0.2f, cy + s * 0.68f, paint);
    }

    private void drawWave(Canvas canvas, float w, float h, float t) {
        float cy = h * 0.85f;
        float left = w * 0.17f;
        float right = w * 0.83f;
        int count = 24;
        float gap = (right - left) / (count - 1);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float v = 0.10f + 0.12f * (float) Math.abs(Math.sin(t * 2.2f + i * 0.55f));
            int idx = history.size() - count + i;
            if (idx >= 0) v = Math.max(v, history.get(idx));
            float bh = Ui.dp(getContext(), 2.2f) + v * Ui.dp(getContext(), 15f);
            paint.setColor(i % 3 == 0 ? 0xFF9FB0FF : 0xFF5F79F5);
            RectF bar = new RectF(left + i * gap - Ui.dp(getContext(), 1.1f), cy - bh / 2,
                    left + i * gap + Ui.dp(getContext(), 1.1f), cy + bh / 2);
            canvas.drawRoundRect(bar, Ui.dp(getContext(), 1.2f), Ui.dp(getContext(), 1.2f), paint);
        }
    }
}
