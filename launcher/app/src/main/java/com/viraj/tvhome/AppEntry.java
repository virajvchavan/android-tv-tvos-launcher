package com.viraj.tvhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

final class AppEntry {
    final ComponentName component;
    final String label;
    final Drawable banner;
    final Drawable icon;
    int glowColor;
    Bitmap card;

    AppEntry(ComponentName component, String label, Drawable banner, Drawable icon) {
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
    }

    String packageName() {
        return component.getPackageName();
    }
}
