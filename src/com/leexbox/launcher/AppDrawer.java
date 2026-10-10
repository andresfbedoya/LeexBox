package com.leexbox.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class AppDrawer {
    private final HomeActivity act;
    private final Prefs prefs;
    private final AppIndex apps;
    private final IconProvider icons;
    private final FrameLayout host;
    private final Adapter adapter = new Adapter();
    private final int slop;
    private List<AppIndex.App> shown = new ArrayList<>();
    private FrameLayout overlay;
    private AbsListView list;
    private EditText search;
    private ValueAnimator anim;
    private boolean grid;
    private boolean closing;
    private float t = 1f;
    private Palette p;

    AppDrawer(HomeActivity act, Prefs prefs, AppIndex apps, IconProvider icons, FrameLayout host) {
        this.act = act;
        this.prefs = prefs;
        this.apps = apps;
        this.icons = icons;
        this.host = host;
        this.slop = ViewConfiguration.get(act).getScaledTouchSlop();
    }

    boolean isShown() {
        return overlay != null && !closing;
    }

    void apply(Palette palette) {
        p = palette;
        if (overlay != null) {
            boolean wasOpen = !closing;
            removeNow();
            if (wasOpen) {
                build();
                applyT(0f);
            }
        }
    }

    void open() {
        if (overlay != null || p == null) return;
        build();
        applyT(1f);
        animateTo(0f, null);
    }

    void close() {
        if (overlay == null || closing) return;
        closing = true;
        animateTo(1f, this::removeNow);
    }

    void beginDrag() {
        if (overlay != null || p == null) return;
        build();
        applyT(1f);
    }

    void dragOpen(float dy) {
        if (overlay == null) return;
        applyT(1f - Math.max(0f, Math.min(1f, dy / Math.max(1f, host.getHeight()))));
    }

    void endDrag(float vy) {
        if (overlay == null) return;
        boolean open = t < 0.65f || vy < -900f;
        if (vy > 900f) open = false;
        if (open) {
            animateTo(0f, null);
        } else {
            closing = true;
            animateTo(1f, this::removeNow);
        }
    }

    private void removeNow() {
        if (anim != null) anim.cancel();
        if (overlay != null) {
            host.removeView(overlay);
            overlay = null;
        }
        closing = false;
    }

    private void applyT(float value) {
        if (overlay == null) return;
        t = value;
        float w = host.getWidth();
        float h = host.getHeight();
        float x = 0f;
        float y = 0f;
        switch (prefs.drawerAnim()) {
            case "top": y = -h * t; break;
            case "left": x = -w * t; break;
            case "right": x = w * t; break;
            default: y = h * t; break;
        }
        overlay.setTranslationX(x);
        overlay.setTranslationY(y);
        overlay.setAlpha(1f - 0.5f * t);
    }

    private void animateTo(float target, Runnable end) {
        if (overlay == null) return;
        if (anim != null) anim.cancel();
        FrameLayout o = overlay;
        ValueAnimator a = ValueAnimator.ofFloat(t, target);
        a.setDuration(Math.round(120 + 200 * Math.abs(target - t)));
        a.setInterpolator(Ui.ease(act));
        a.addUpdateListener(v -> applyT((Float) v.getAnimatedValue()));
        boolean[] cancelled = {false};
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator x) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator x) {
                o.setLayerType(View.LAYER_TYPE_NONE, null);
                if (!cancelled[0] && end != null) end.run();
            }
        });
        o.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        anim = a;
        a.start();
    }

    private void build() {
        grid = prefs.drawerGrid();
        closing = false;
        FrameLayout o = new FrameLayout(act);
        overlay = o;
        o.setBackgroundColor(Ui.alpha(p.base, 0xF2));
        o.setClickable(true);

        int pad = Ui.dp(act, 16);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, Ui.dp(act, 6), 0);

        search = new EditText(act);
        search.setHint("Buscar app");
        search.setTextColor(p.text);
        search.setHintTextColor(p.textDim);
        search.setTextSize(16);
        search.setBackground(Ui.round(p.surface, Ui.dp(act, 24)));
        search.setPadding(pad, Ui.dp(act, 12), pad, Ui.dp(act, 12));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });
        search.setOnEditorActionListener((v, id, e) -> {
            if (!shown.isEmpty()) open(shown.get(0));
            return true;
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.rightMargin = Ui.dp(act, 10);
        col.addView(search, sp);

        if (grid) {
            GridView g = new GridView(act);
            g.setNumColumns(4);
            g.setVerticalSpacing(Ui.dp(act, 12));
            g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            g.setSelector(new ColorDrawable(Color.TRANSPARENT));
            list = g;
        } else {
            ListView l = new ListView(act);
            l.setDivider(null);
            list = l;
        }
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> open(shown.get(pos)));
        list.setOnItemLongClickListener((parent, v, pos, id) -> {
            options(shown.get(pos));
            return true;
        });

        AlphaBar bar = new AlphaBar(act);
        bar.setColors(p.textDim, p.primary);
        TextView bubble = Ui.text(act, "", 28, p.onPrimary);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(Ui.round(p.primary, Ui.dp(act, 36)));
        bubble.setVisibility(View.GONE);
        bar.setListener(new AlphaBar.Listener() {
            @Override
            public void onLetter(char letter) {
                bubble.setText(String.valueOf(letter));
                bubble.setVisibility(View.VISIBLE);
                list.setSelection(indexFor(letter));
            }

            @Override
            public void onRelease() {
                bubble.setVisibility(View.GONE);
            }
        });

        LinearLayout body = new LinearLayout(act);
        body.addView(list, new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(bar, new LinearLayout.LayoutParams(Ui.dp(act, 26), -1));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.topMargin = Ui.dp(act, 8);
        bp.bottomMargin = Ui.dp(act, 8);
        col.addView(body, bp);

        DragLayout drag = new DragLayout(act);
        drag.addView(col, new FrameLayout.LayoutParams(-1, -1));
        o.addView(drag, new FrameLayout.LayoutParams(-1, -1));
        o.addView(bubble, new FrameLayout.LayoutParams(Ui.dp(act, 72), Ui.dp(act, 72), Gravity.CENTER));
        host.addView(o, new FrameLayout.LayoutParams(-1, -1));
        filter("");
    }

    private int indexFor(char letter) {
        if (letter == '#') return 0;
        for (int i = 0; i < shown.size(); i++) {
            String n = AppIndex.norm(shown.get(i).label);
            if (!n.isEmpty() && Character.toUpperCase(n.charAt(0)) >= letter) return i;
        }
        return Math.max(0, shown.size() - 1);
    }

    private void open(AppIndex.App app) {
        close();
        apps.launch(act, app);
    }

    private void filter(String query) {
        String q = AppIndex.norm(query);
        List<AppIndex.App> next = new ArrayList<>();
        for (AppIndex.App a : apps.visible()) {
            if (q.isEmpty() || AppIndex.norm(a.label).contains(q)) next.add(a);
        }
        shown = next;
        adapter.notifyDataSetChanged();
    }

    private void options(AppIndex.App app) {
        new AlertDialog.Builder(act)
                .setTitle(app.label)
                .setItems(new String[]{"Ocultar del cajón", "Información de la app"}, (d, which) -> {
                    if (which == 0) {
                        Set<String> hidden = prefs.hidden();
                        hidden.add(app.pkg);
                        prefs.hidden(hidden);
                        filter(search.getText().toString());
                    } else {
                        act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + app.pkg)));
                    }
                })
                .show();
    }

    private final class DragLayout extends FrameLayout {
        private float downX;
        private float downY;
        private float startAlong;
        private boolean dragging;
        private VelocityTracker tracker;

        DragLayout(Context c) {
            super(c);
        }

        private float along(MotionEvent e) {
            switch (prefs.drawerAnim()) {
                case "top": return downY - e.getRawY();
                case "left": return downX - e.getRawX();
                case "right": return e.getRawX() - downX;
                default: return e.getRawY() - downY;
            }
        }

        private float size() {
            String d = prefs.drawerAnim();
            return Math.max(1f, d.equals("left") || d.equals("right") ? host.getWidth() : host.getHeight());
        }

        private boolean closeIntent(MotionEvent e) {
            float dx = e.getRawX() - downX;
            float dy = e.getRawY() - downY;
            float min = slop * 1.5f;
            switch (prefs.drawerAnim()) {
                case "top": return -dy > min && -dy > Math.abs(dx) * 1.5f && !list.canScrollVertically(1);
                case "left": return -dx > min && -dx > Math.abs(dy) * 1.5f;
                case "right": return dx > min && dx > Math.abs(dy) * 1.5f;
                default: return dy > min && dy > Math.abs(dx) * 1.5f && !list.canScrollVertically(-1);
            }
        }

        private float closingVelocity() {
            switch (prefs.drawerAnim()) {
                case "top": return -tracker.getYVelocity();
                case "left": return -tracker.getXVelocity();
                case "right": return tracker.getXVelocity();
                default: return tracker.getYVelocity();
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    if (tracker != null) tracker.recycle();
                    tracker = VelocityTracker.obtain();
                    tracker.addMovement(e);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (tracker != null) tracker.addMovement(e);
                    if (!dragging && !closing && closeIntent(e)) {
                        dragging = true;
                        startAlong = along(e);
                        if (anim != null) anim.cancel();
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!dragging) return false;
            tracker.addMovement(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    applyT(Math.max(0f, Math.min(1f, (along(e) - startAlong) / size())));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    dragging = false;
                    tracker.computeCurrentVelocity(1000);
                    float v = closingVelocity();
                    boolean shut = t > 0.35f || v > 900f;
                    if (v < -900f) shut = false;
                    if (shut) {
                        closing = true;
                        animateTo(1f, AppDrawer.this::removeNow);
                    } else {
                        animateTo(0f, null);
                    }
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            if (convert == null) {
                row = new LinearLayout(act);
                ImageView icon = new ImageView(act);
                TextView label = new TextView(act);
                if (grid) {
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setGravity(Gravity.CENTER_HORIZONTAL);
                    row.setPadding(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
                    row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 56), Ui.dp(act, 56)));
                    label.setGravity(Gravity.CENTER);
                    label.setMaxLines(1);
                    label.setEllipsize(TextUtils.TruncateAt.END);
                    label.setTextSize(12);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.topMargin = Ui.dp(act, 6);
                    row.addView(label, lp);
                } else {
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(act, 8), Ui.dp(act, 6), Ui.dp(act, 8), Ui.dp(act, 6));
                    row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 44)));
                    label.setTextSize(16);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.leftMargin = Ui.dp(act, 16);
                    row.addView(label, lp);
                }
            } else {
                row = (LinearLayout) convert;
            }
            AppIndex.App app = shown.get(i);
            ((ImageView) row.getChildAt(0)).setImageDrawable(icons.get(app, p));
            TextView label = (TextView) row.getChildAt(1);
            label.setText(app.label);
            label.setTextColor(p.text);
            return row;
        }
    }
}
