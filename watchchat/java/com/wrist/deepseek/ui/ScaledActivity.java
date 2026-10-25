package com.wrist.deepseek.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import com.wrist.deepseek.core.Settings;

/** Applies the user's UI scale by overriding density before any view is created. */
public class ScaledActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        float scale = new Settings(newBase).getUiScale();
        if (Math.abs(scale - 1f) < 0.001f) {
            super.attachBaseContext(newBase);
            return;
        }
        Configuration cfg = new Configuration(newBase.getResources().getConfiguration());
        cfg.densityDpi = Math.max(72, Math.round(cfg.densityDpi * scale));
        super.attachBaseContext(newBase.createConfigurationContext(cfg));
    }
}
