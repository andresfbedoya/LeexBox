package com.leexbox.launcher;

import android.content.Context;
import android.graphics.Color;

final class Palette {
    final int primary;
    final int onPrimary;
    final int container;
    final int onContainer;
    final int surface;
    final int text;
    final int textDim;
    final int base;

    private Palette(int primary, int onPrimary, int container, int onContainer, int surface, int text, int textDim, int base) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.container = container;
        this.onContainer = onContainer;
        this.surface = surface;
        this.text = text;
        this.textDim = textDim;
        this.base = base;
    }

    static Palette resolve(Context c, boolean materialYou, int seed) {
        if (materialYou) {
            return new Palette(
                    c.getColor(android.R.color.system_accent1_200),
                    c.getColor(android.R.color.system_accent1_800),
                    c.getColor(android.R.color.system_accent2_700),
                    c.getColor(android.R.color.system_accent2_100),
                    c.getColor(android.R.color.system_neutral1_800),
                    c.getColor(android.R.color.system_neutral1_100),
                    c.getColor(android.R.color.system_neutral2_300),
                    c.getColor(android.R.color.system_neutral1_900));
        }
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float h = hsv[0];
        return new Palette(
                tone(h, 0.45f, 0.95f),
                tone(h, 0.80f, 0.25f),
                tone(h, 0.50f, 0.35f),
                tone(h, 0.20f, 0.95f),
                tone(h, 0.25f, 0.18f),
                Color.rgb(236, 236, 242),
                Color.rgb(172, 172, 184),
                tone(h, 0.30f, 0.07f));
    }

    int scrim(int percent) {
        return Ui.alpha(base, Math.round(percent * 255f / 100f));
    }

    private static int tone(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }
}
