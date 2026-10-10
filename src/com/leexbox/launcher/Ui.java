package com.leexbox.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    static ColorStateList checked(int on, int off) {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{on, off});
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static TextView pill(Context c, String s, int bg, int fg, View.OnClickListener l) {
        TextView t = text(c, s, 14, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setBackground(round(bg, dp(c, 24)));
        t.setOnClickListener(l);
        return t;
    }

    static boolean hit(View v, float rx, float ry) {
        if (!v.isShown()) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rx >= loc[0] && rx <= loc[0] + v.getWidth() && ry >= loc[1] && ry <= loc[1] + v.getHeight();
    }

    static Interpolator ease(Context c) {
        return AnimationUtils.loadInterpolator(c, android.R.interpolator.fast_out_slow_in);
    }
}
