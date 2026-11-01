package com.intellirs.reader.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.util.ThemeManager;

/** Applies app font scale, theme, accent and edge-to-edge insets consistently. */
public abstract class BaseActivity extends AppCompatActivity {
    protected AppContainer container;
    protected SettingsRepository settings;
    private String appliedAccent;
    private float appliedFontScale;

    @Override
    protected void attachBaseContext(Context newBase) {
        // App-level font scaling without altering the user's system-wide setting.
        float scale = newBase.getSharedPreferences(SettingsRepository.PREFS_NAME,
                        Context.MODE_PRIVATE)
                .getFloat(SettingsRepository.KEY_APP_FONT_SCALE, 1f);
        scale = Math.max(0.8f, Math.min(1.35f, scale));
        Configuration configuration = new Configuration(
                newBase.getResources().getConfiguration());
        configuration.fontScale = scale;
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyNightMode(this);
        super.onCreate(savedInstanceState);
        container = ((IntelliRssApp) getApplication()).getContainer();
        settings = container.settingsRepository();
        appliedAccent = settings.getAccent();
        appliedFontScale = settings.getAppFontScale();
        ThemeManager.applyAccent(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View content = findViewById(android.R.id.content);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), content);
        controller.setAppearanceLightStatusBars(!ThemeManager.isDark(this));
        controller.setAppearanceLightNavigationBars(!ThemeManager.isDark(this));
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(0, bars.top, 0, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }

    public AppContainer container() {
        return container;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings != null
                && (Math.abs(appliedFontScale - settings.getAppFontScale()) > 0.001f
                || !appliedAccent.equals(settings.getAccent()))) {
            recreate();
        }
    }
}
