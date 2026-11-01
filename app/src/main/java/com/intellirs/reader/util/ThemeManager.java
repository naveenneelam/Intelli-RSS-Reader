package com.intellirs.reader.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.intellirs.reader.R;
import com.intellirs.reader.data.repository.SettingsRepository;

/** Central theme/accent/font resolution for the native Views UI. */
public final class ThemeManager {
    private ThemeManager() {
    }

    public static void applyNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString(SettingsRepository.KEY_THEME, "dark");
        int target;
        if ("light".equals(mode)) {
            target = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("system".equals(mode)) {
            target = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            target = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target);
        }
    }

    public static void applyAccent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE);
        String accent = prefs.getString(SettingsRepository.KEY_ACCENT, "teal");
        int overlay;
        switch (accent) {
            case "blue":
                overlay = R.style.ThemeOverlay_IntelliRss_Accent_Blue;
                break;
            case "cyan":
                overlay = R.style.ThemeOverlay_IntelliRss_Accent_Cyan;
                break;
            case "amber":
                overlay = R.style.ThemeOverlay_IntelliRss_Accent_Amber;
                break;
            case "teal":
            default:
                overlay = R.style.ThemeOverlay_IntelliRss_Accent_Teal;
                break;
        }
        context.getTheme().applyStyle(overlay, true);
    }

    public static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int color(Context context, int colorResource) {
        return ContextCompat.getColor(context, colorResource);
    }
}
