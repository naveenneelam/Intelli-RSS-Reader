package com.intellirs.reader.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Deterministic generated publisher icon used when no downloaded favicon exists. */
public class LetterIconDrawable extends Drawable {
    private static final int[] BACKGROUNDS = new int[]{
            0xFF164E48, 0xFF1C3E5B, 0xFF5A4218, 0xFF4A2F52, 0xFF3E4A24, 0xFF4B3030
    };
    private static final int[] FOREGROUNDS = new int[]{
            0xFFA7F2E4, 0xFFC5DAFF, 0xFFFFDFA0, 0xFFEBCBFF, 0xFFD9EBA5, 0xFFFFD0C9
    };
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String initials;
    private final int size;

    public LetterIconDrawable(String title, int sizePx) {
        this.size = sizePx;
        int hash = Math.abs((title == null ? "" : title).hashCode());
        int index = hash % BACKGROUNDS.length;
        background.setColor(BACKGROUNDS[index]);
        text.setColor(FOREGROUNDS[index]);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextSize(sizePx * 0.40f);
        initials = TextUtils.initials(title);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.22f;
        canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom,
                radius, radius, background);
        Paint.FontMetrics metrics = text.getFontMetrics();
        float centerY = bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(initials, bounds.exactCenterX(), centerY, text);
    }

    @Override
    public void setAlpha(int alpha) {
        background.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        background.setColorFilter(colorFilter);
        text.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}
