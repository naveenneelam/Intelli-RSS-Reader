package com.intellirs.reader.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.intellirs.reader.R;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small native bar chart; no chart SDK or WebView. */
public class ReadingBarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] values = new int[14];
    private LocalDate[] days = new LocalDate[14];

    public ReadingBarChartView(Context context) {
        super(context);
        init(context);
    }

    public ReadingBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ReadingBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        barPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.on_surface_variant));
        labelPaint.setTextSize(sp(10));
        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        gridPaint.setStrokeWidth(dp(1));
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            days[i] = today.minusDays(13 - i);
            values[i] = 0;
        }
    }

    public void setValues(int[] values) {
        if (values != null && values.length == 14) {
            this.values = values;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int left = dp(4);
        int right = width - dp(4);
        int top = dp(18);
        int bottom = height - dp(24);
        int max = 1;
        for (int value : values) {
            max = Math.max(max, value);
        }
        float slot = (right - left) / 14f;
        float barWidth = Math.max(dp(6), slot * 0.55f);
        canvas.drawLine(left, bottom, right, bottom, gridPaint);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
        for (int i = 0; i < 14; i++) {
            float x = left + slot * i + (slot - barWidth) / 2f;
            float barHeight = (bottom - top) * values[i] / (float) max;
            RectF rect = new RectF(x, bottom - barHeight, x + barWidth, bottom);
            canvas.drawRoundRect(rect, dp(3), dp(3), barPaint);
            if (values[i] > 0) {
                String count = String.valueOf(values[i]);
                float countWidth = labelPaint.measureText(count);
                canvas.drawText(count, x + (barWidth - countWidth) / 2f,
                        bottom - barHeight - dp(3), labelPaint);
            }
            if (i % 3 == 0 || i == 13) {
                String day = days[i].format(formatter);
                float textWidth = labelPaint.measureText(day);
                canvas.drawText(day, x + (barWidth - textWidth) / 2f,
                        height - dp(5), labelPaint);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
