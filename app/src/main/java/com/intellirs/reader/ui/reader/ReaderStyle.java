package com.intellirs.reader.ui.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;

import com.intellirs.reader.data.repository.SettingsRepository;

/** Resolved reading appearance independent of the app chrome theme. */
public class ReaderStyle {
    public int background;
    public int text;
    public int secondaryText;
    public int card;
    public int divider;
    public int codeBackground;
    public int accent;
    public int fontSp;
    public float lineSpacing;
    public Typeface typeface;
    public boolean dark;

    public static ReaderStyle resolve(Context context, SettingsRepository settings) {
        ReaderStyle style = new ReaderStyle();
        String theme = settings.getReaderTheme();
        if ("light".equals(theme)) {
            style.background = Color.parseColor("#FAF9F6");
            style.text = Color.parseColor("#1B2225");
            style.secondaryText = Color.parseColor("#5A6468");
            style.card = Color.parseColor("#FFFFFF");
            style.divider = Color.parseColor("#E2E4E2");
            style.codeBackground = Color.parseColor("#F0EFEA");
            style.accent = Color.parseColor("#00695F");
            style.dark = false;
        } else if ("sepia".equals(theme)) {
            style.background = Color.parseColor("#F3E8D0");
            style.text = Color.parseColor("#3D2F1F");
            style.secondaryText = Color.parseColor("#6B5842");
            style.card = Color.parseColor("#EADCC3");
            style.divider = Color.parseColor("#D5C5A5");
            style.codeBackground = Color.parseColor("#E5D5B7");
            style.accent = Color.parseColor("#7A4700");
            style.dark = false;
        } else {
            style.background = Color.parseColor("#11181B");
            style.text = Color.parseColor("#DDE7EA");
            style.secondaryText = Color.parseColor("#9FB1B8");
            style.card = Color.parseColor("#182226");
            style.divider = Color.parseColor("#2A3A40");
            style.codeBackground = Color.parseColor("#0B1012");
            style.accent = Color.parseColor("#5ADCC8");
            style.dark = true;
        }
        style.fontSp = settings.getReaderFontSize();
        style.lineSpacing = settings.getReaderLineSpacing();
        switch (settings.getReaderFontFamily()) {
            case "sans":
                style.typeface = Typeface.create("sans-serif", Typeface.NORMAL);
                break;
            case "mono":
                style.typeface = Typeface.MONOSPACE;
                break;
            case "serif":
            default:
                style.typeface = Typeface.SERIF;
                break;
        }
        return style;
    }
}
