package com.leexbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

final class ElementView extends FrameLayout {
    interface Host {
        void onChanged(Screens.Item item);

        void onLongPress(ElementView view);

        void onConfigure(ElementView view);

        void onRemove(ElementView view);
    }

    final Screens.Item item;
    private final View content;
    private final Host host;
    private final int slop;
    private final Runnable longPress;
    private Chrome chrome;
    private boolean editing;
    private boolean fired;
    private float downX;
    private float downY;

    ElementView(Context c, Screens.Item item, View content, Host host) {
        super(c);
        this.item = item;
        this.content = content;
        this.host = host;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        this.longPress = () -> {
            fired = true;
            this.host.onLongPress(this);
        };
        if (content.getParent() instanceof ViewGroup) {
            ((ViewGroup) content.getParent()).removeView(content);
        }
        addView(content, new FrameLayout.LayoutParams(-1, -1));
        setLayoutParams(new PageLayout.LP(item));
    }

    View content() {
        return content;
    }

    void setEditing(boolean on, int accent) {
        editing = on;
        removeCallbacks(longPress);
        if (chrome != null) {
            removeView(chrome);
            chrome = null;
        }
        if (on) {
            chrome = new Chrome(getContext(), accent);
            addView(chrome, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (editing) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                downX = e.getX();
                downY = e.getY();
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    removeCallbacks(longPress);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                break;
            default:
                break;
        }
        return fired;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private final class Chrome extends FrameLayout {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint();
        private float startX;
        private float startY;
        private float baseX;
        private float baseY;
        private float baseW;
        private float baseH;

        Chrome(Context c, int accent) {
            super(c);
            setWillNotDraw(false);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Ui.dp(c, 2));
            stroke.setColor(accent);
            fill.setColor(Ui.alpha(accent, 0x26));

            int size = Ui.dp(c, 36);
            if (item.resizable()) {
                View handle = new View(c);
                handle.setBackground(Ui.round(accent, Ui.dp(c, 14)));
                handle.setOnTouchListener(this::resize);
                addView(handle, new FrameLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32), Gravity.BOTTOM | Gravity.END));
            }
            if (item.kind.equals("widget")) {
                addView(button("⚙", accent, () -> host.onConfigure(ElementView.this)),
                        new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START));
            }
            if (!item.kind.equals("terminal")) {
                addView(button("✕", accent, () -> host.onRemove(ElementView.this)),
                        new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END));
            }
        }

        private TextView button(String label, int accent, Runnable action) {
            TextView t = Ui.text(getContext(), label, 16, 0xFF000000);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.round(accent, Ui.dp(getContext(), 18)));
            t.setOnClickListener(v -> action.run());
            return t;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
            canvas.drawRect(1, 1, getWidth() - 1, getHeight() - 1, stroke);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            View page = (View) ElementView.this.getParent();
            if (page == null) return true;
            float pw = Math.max(1, page.getWidth());
            float ph = Math.max(1, page.getHeight());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX();
                    startY = e.getRawY();
                    baseX = item.x;
                    baseY = item.y;
                    ElementView.this.bringToFront();
                    ElementView.this.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120)
                            .setInterpolator(Ui.ease(getContext())).start();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float nx = clamp(baseX + (e.getRawX() - startX) / pw, 0f, 1f - item.w);
                    float ny = clamp(baseY + (e.getRawY() - startY) / ph, 0f, 1f - item.h);
                    ElementView.this.setTranslationX((nx - baseX) * pw);
                    ElementView.this.setTranslationY((ny - baseY) * ph);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    item.x = clamp(baseX + ElementView.this.getTranslationX() / pw, 0f, 1f - item.w);
                    item.y = clamp(baseY + ElementView.this.getTranslationY() / ph, 0f, 1f - item.h);
                    ElementView.this.setTranslationX(0f);
                    ElementView.this.setTranslationY(0f);
                    ElementView.this.animate().scaleX(1f).scaleY(1f).setDuration(140)
                            .setInterpolator(Ui.ease(getContext())).start();
                    page.requestLayout();
                    host.onChanged(item);
                    return true;
                default:
                    return true;
            }
        }

        private boolean resize(View v, MotionEvent e) {
            View page = (View) ElementView.this.getParent();
            if (page == null) return true;
            float pw = Math.max(1, page.getWidth());
            float ph = Math.max(1, page.getHeight());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    startX = e.getRawX();
                    startY = e.getRawY();
                    baseW = item.w;
                    baseH = item.h;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    item.w = clamp(baseW + (e.getRawX() - startX) / pw, item.minW(), 1f - item.x);
                    item.h = clamp(baseH + (e.getRawY() - startY) / ph, item.minH(), 1f - item.y);
                    page.requestLayout();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    host.onChanged(item);
                    return true;
                default:
                    return true;
            }
        }
    }
}
