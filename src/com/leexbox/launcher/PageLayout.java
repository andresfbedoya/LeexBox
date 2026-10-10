package com.leexbox.launcher;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

final class PageLayout extends FrameLayout {
    static final class LP extends FrameLayout.LayoutParams {
        final Screens.Item item;

        LP(Screens.Item item) {
            super(-1, -1);
            this.item = item;
        }
    }

    private boolean focusTerminal;

    PageLayout(Context c) {
        super(c);
    }

    void setFocusTerminal(boolean on) {
        focusTerminal = on;
        requestLayout();
    }

    private float[] geo(Screens.Item it) {
        if (focusTerminal) {
            return it.kind.equals("terminal") ? new float[]{0f, 0f, 1f, 1f} : new float[]{0f, 0f, 0f, 0f};
        }
        return new float[]{it.x, it.y, it.w, it.h};
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = View.MeasureSpec.getSize(widthSpec);
        int h = View.MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            float[] g = geo(((LP) v.getLayoutParams()).item);
            v.measure(View.MeasureSpec.makeMeasureSpec(Math.round(g[2] * w), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Math.round(g[3] * h), View.MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            float[] g = geo(((LP) v.getLayoutParams()).item);
            int x = Math.round(g[0] * w);
            int y = Math.round(g[1] * h);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }
}
