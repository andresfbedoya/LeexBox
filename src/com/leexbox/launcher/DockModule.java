package com.leexbox.launcher;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

final class DockModule {
    static final int MAX = 5;

    private final HomeActivity act;
    private final Prefs prefs;
    private final AppIndex apps;
    private final IconProvider icons;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private boolean hasApps;
    private boolean wanted = true;
    private boolean shown;

    DockModule(HomeActivity act, Prefs prefs, AppIndex apps, IconProvider icons) {
        this.act = act;
        this.prefs = prefs;
        this.apps = apps;
        this.icons = icons;

        row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(act, 8);
        row.setPadding(pad, pad, pad, pad);

        scroll = new HorizontalScrollView(act);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToOutline(true);
        scroll.setVisibility(View.GONE);
        scroll.addView(row);
    }

    View view() {
        return scroll;
    }

    boolean hit(float x, float y) {
        return Ui.hit(scroll, x, y);
    }

    void setVisible(boolean visible) {
        wanted = visible;
        boolean show = wanted && hasApps;
        if (show == shown) return;
        shown = show;
        scroll.animate().cancel();
        float shift = Ui.dp(act, 28);
        if (show) {
            scroll.setVisibility(View.VISIBLE);
            scroll.setAlpha(0f);
            scroll.setTranslationY(shift);
            scroll.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(Ui.ease(act)).withLayer().start();
        } else {
            scroll.animate().alpha(0f).translationY(shift).setDuration(180).setInterpolator(Ui.ease(act)).withLayer().withEndAction(() -> {
                if (!shown) scroll.setVisibility(View.GONE);
            }).start();
        }
    }

    void refresh(Palette p) {
        row.removeAllViews();
        int size = Ui.dp(act, 52);
        int gap = Ui.dp(act, 4);
        int count = 0;
        for (String pkg : prefs.dock()) {
            if (count >= MAX) break;
            AppIndex.App app = apps.byPackage(pkg);
            if (app == null) continue;
            count++;
            ImageView v = new ImageView(act);
            v.setImageDrawable(icons.get(app, p));
            v.setContentDescription(app.label);
            v.setOnClickListener(x -> apps.launch(act, app));
            v.setOnTouchListener((view, e) -> {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.86f).scaleY(0.86f).setDuration(80).start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(gap, 0, gap, 0);
            row.addView(v, lp);
        }
        hasApps = count > 0;
        scroll.setBackground(prefs.dockTransparent() ? null : Ui.round(Ui.alpha(p.base, 0xCC), Ui.dp(act, 36)));
        setVisible(wanted);
    }
}
