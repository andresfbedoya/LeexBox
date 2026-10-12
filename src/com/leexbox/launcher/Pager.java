package com.leexbox.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Iterator;
import java.util.List;

final class Pager implements ElementView.Host {
    private static final float EDIT_SCALE = 0.78f;

    private final HomeActivity act;
    private final Prefs prefs;
    private final AppIndex apps;
    private final IconProvider icons;
    private final Screens screens;
    private final TerminalModule terminal;
    private final StatsModule stats;
    private final WidgetsModule widgets;
    private final FrameLayout root;
    private final LinearLayout dots;
    private final LinearLayout toolbar;
    private TextView counter;
    private PageLayout pageView;
    private PageLayout neighbor;
    private PageLayout peekPrev;
    private PageLayout peekNext;
    private int neighborIndex = -1;
    private int swipeDir = 1;
    private float swipeW = 1f;
    private Palette p;
    private boolean editing;
    private boolean animating;
    private boolean ime;

    Pager(HomeActivity act, Prefs prefs, AppIndex apps, IconProvider icons,
          TerminalModule terminal, StatsModule stats, WidgetsModule widgets) {
        this.act = act;
        this.prefs = prefs;
        this.apps = apps;
        this.icons = icons;
        this.terminal = terminal;
        this.stats = stats;
        this.widgets = widgets;
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        this.screens = new Screens(prefs, dm.heightPixels / dm.density - 140f);

        root = new FrameLayout(act);
        root.setClipChildren(true);

        dots = new LinearLayout(act);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 2));

        toolbar = new LinearLayout(act);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(act, 8);
        root.addView(toolbar, lp);
    }

    View view() {
        return root;
    }

    View dotsView() {
        return dots;
    }

    boolean isEditing() {
        return editing;
    }

    int pageCount() {
        return screens.pages.size();
    }

    int pageIndex() {
        return screens.current;
    }

    void apply(Palette palette) {
        p = palette;
        buildToolbar();
        reload();
    }

    void reload() {
        if (p == null) return;
        dropNeighbor();
        dropPeeks();
        if (pageView != null) root.removeView(pageView);
        pageView = build(screens.current, true);
        root.addView(pageView, 0, new FrameLayout.LayoutParams(-1, -1));
        if (editing) makePeeks();
        updateDots();
        updateCounter();
        sizeWidgets();
    }

    void commit() {
        screens.save();
        reload();
    }

    void removeItem(Screens.Item item) {
        screens.page().remove(item);
        commit();
    }

    void setIme(boolean visible) {
        if (ime == visible) return;
        ime = visible;
        if (pageView == null) return;
        pageView.setFocusTerminal(visible);
        for (int i = 0; i < pageView.getChildCount(); i++) {
            ElementView ev = (ElementView) pageView.getChildAt(i);
            if (!ev.item.kind.equals("terminal")) ev.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        }
        updateDots();
    }

    void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        toolbar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (pageView != null) {
            makeChrome(pageView, on);
            pageView.animate().scaleX(on ? EDIT_SCALE : 1f).scaleY(on ? EDIT_SCALE : 1f)
                    .setDuration(260).setInterpolator(Ui.ease(act)).start();
        }
        if (on) {
            makePeeks();
            fade(peekPrev, true);
            fade(peekNext, true);
        } else {
            dropNeighbor();
            fadeOutPeeks();
        }
        updateCounter();
    }

    void beginSwipe() {
        swipeW = slot();
    }

    void swipeBy(float dx) {
        if (pageView == null || animating) return;
        int dir = dx < 0 ? 1 : -1;
        swipeDir = dir;
        int want = screens.current + dir;
        boolean has = want >= 0 && want < screens.pages.size();
        if (has && (neighbor == null || neighborIndex != want)) prepareNeighbor(want, dir);
        if (!has) dropNeighbor();
        float shift = has ? dx : dx * 0.25f;
        pageView.setTranslationX(shift);
        if (neighbor != null) neighbor.setTranslationX(shift + dir * swipeW);
        PageLayout opp = opposite();
        if (opp != null) opp.setTranslationX(shift - dir * swipeW);
    }

    void endSwipe(float vx) {
        if (pageView == null || animating) return;
        float shift = pageView.getTranslationX();
        boolean commit = neighbor != null
                && (Math.abs(shift) > swipeW * 0.3f || (Math.abs(vx) > 800f && vx * shift > 0f));
        settle(commit);
    }

    void go(int delta) {
        if (animating || pageView == null) return;
        int target = screens.current + delta;
        if (target < 0 || target >= screens.pages.size()) return;
        beginSwipe();
        int dir = delta > 0 ? 1 : -1;
        swipeDir = dir;
        prepareNeighbor(target, dir);
        neighbor.setTranslationX(dir * swipeW);
        settle(true);
    }

    void addScreen() {
        screens.addPage();
        go(screens.pages.size() - 1 - screens.current);
        updateDots();
    }

    List<Integer> removeScreen() {
        List<Integer> ids = screens.removePage(screens.current);
        reload();
        return ids;
    }

    List<Integer> resetLayout() {
        List<Integer> ids = screens.reset();
        reload();
        return ids;
    }

    void regrid() {
        screens.snapAll(prefs.gridCols(), prefs.gridRows());
        reload();
    }

    void addWidget(int id, AppWidgetProviderInfo info) {
        float pw = Math.max(1f, root.getWidth());
        float ph = Math.max(1f, root.getHeight());
        int cols = prefs.gridCols();
        int rows = prefs.gridRows();
        Screens.Item it;
        if (cols > 0 && rows > 0) {
            int wc = Math.max(1, Math.min(cols, (int) Math.ceil(info.minWidth / (pw / cols))));
            int hc = Math.max(1, Math.min(rows, (int) Math.ceil(info.minHeight / (ph / rows))));
            float[] pos = screens.firstFree(screens.page(), cols, rows, wc, hc);
            it = new Screens.Item("widget", id, "", pos[0], pos[1], wc / (float) cols, hc / (float) rows);
        } else {
            float w = Math.min(0.96f, Math.max(0.25f, info.minWidth / pw));
            float h = Math.min(0.9f, Math.max(0.1f, info.minHeight / ph));
            it = new Screens.Item("widget", id, "", (1f - w) / 2f, 0.08f, w, h);
            if (it.y + it.h > 1f) it.y = 1f - it.h;
        }
        screens.page().add(it);
        screens.save();
        reload();
    }

    void addApp(String pkg) {
        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        float pw = Math.max(1f, root.getWidth());
        float ph = Math.max(1f, root.getHeight());
        int cols = prefs.gridCols();
        int rows = prefs.gridRows();
        Screens.Item it;
        if (cols > 0 && rows > 0) {
            float[] pos = screens.firstFree(screens.page(), cols, rows, 1, 1);
            it = new Screens.Item("app", -1, pkg, pos[0], pos[1], 1f / cols, 1f / rows);
        } else {
            float w = Math.min(0.4f, 84f * dm.density / pw);
            float h = Math.min(0.3f, 92f * dm.density / ph);
            int n = screens.count("app", screens.page());
            it = new Screens.Item("app", -1, pkg, Math.min(1f - w, 0.04f + (n % 4) * (w + 0.02f)),
                    Math.min(1f - h, 0.1f + (n / 4) * (h + 0.02f)), w, h);
        }
        screens.page().add(it);
        screens.save();
        reload();
    }

    void ensureStats(boolean on) {
        if (on && !screens.has("stats")) {
            screens.page().add(new Screens.Item("stats", -1, "", 0.15f, 0.01f, 0.7f, 0.06f));
            screens.save();
            reload();
        } else if (!on && screens.has("stats")) {
            screens.removeKind("stats");
            reload();
        }
    }

    boolean hitWidget(float x, float y) {
        return hit(x, y, true);
    }

    boolean hitElement(float x, float y) {
        return hit(x, y, false);
    }

    @Override
    public void onChanged(Screens.Item item) {
        screens.snap(item, prefs.gridCols(), prefs.gridRows());
        screens.save();
        sizeWidgets();
    }

    @Override
    public void onLongPress(ElementView view) {
        if (editing) return;
        switch (view.item.kind) {
            case "terminal":
                act.showSettings();
                break;
            case "app":
                act.appMenu(view.item);
                break;
            default:
                act.enterEdit();
                break;
        }
    }

    @Override
    public void onConfigure(ElementView view) {
        act.configureWidget(view.item.widgetId);
    }

    @Override
    public void onRemove(ElementView view) {
        if (view.item.kind.equals("widget")) {
            act.deleteWidgetId(view.item.widgetId);
        } else if (view.item.kind.equals("stats")) {
            act.prefs().showStats(false);
        }
        screens.page().remove(view.item);
        screens.save();
        reload();
    }

    private boolean hit(float x, float y, boolean widgetsOnly) {
        if (pageView == null) return false;
        Rect r = new Rect();
        for (int i = 0; i < pageView.getChildCount(); i++) {
            ElementView ev = (ElementView) pageView.getChildAt(i);
            if (widgetsOnly && !ev.item.kind.equals("widget")) continue;
            if (ev.getVisibility() == View.VISIBLE && ev.getGlobalVisibleRect(r) && r.contains((int) x, (int) y)) {
                return true;
            }
        }
        return false;
    }

    private float slot() {
        float w = Math.max(1f, root.getWidth());
        return editing ? w * EDIT_SCALE + Ui.dp(act, 14) : w;
    }

    private PageLayout opposite() {
        return editing ? (swipeDir > 0 ? peekPrev : peekNext) : null;
    }

    private PageLayout build(int index, boolean interactive) {
        PageLayout page = new PageLayout(act);
        boolean pruned = false;
        for (Iterator<Screens.Item> it = screens.pages.get(index).iterator(); it.hasNext(); ) {
            Screens.Item item = it.next();
            View content = contentFor(item);
            if (content == null) {
                if (item.kind.equals("widget")) act.deleteWidgetId(item.widgetId);
                it.remove();
                pruned = true;
                continue;
            }
            ElementView ev = new ElementView(act, item, content, this);
            if (editing && interactive) ev.setEditing(true, p.primary);
            if (ime && interactive && !item.kind.equals("terminal")) ev.setVisibility(View.INVISIBLE);
            page.addView(ev);
        }
        if (pruned) screens.save();
        page.setFocusTerminal(interactive && ime && index == screens.current);
        page.setGrid(editing && interactive ? prefs.gridCols() : 0, prefs.gridRows(), Ui.alpha(p.textDim, 0x80));
        if (interactive) {
            page.setOnLongClickListener(v -> {
                if (!editing) act.showSettings();
                return true;
            });
        } else {
            page.setPassive(true);
        }
        if (editing) {
            page.setScaleX(EDIT_SCALE);
            page.setScaleY(EDIT_SCALE);
        }
        return page;
    }

    private View contentFor(Screens.Item item) {
        switch (item.kind) {
            case "terminal":
                return terminal.view();
            case "stats":
                return stats.view();
            case "app":
                return appView(item);
            default:
                AppWidgetProviderInfo info = widgets.manager().getAppWidgetInfo(item.widgetId);
                return info == null ? null : widgets.host().createView(act, item.widgetId, info);
        }
    }

    private View appView(Screens.Item item) {
        Shortcut sc = Shortcut.parse(item.data);
        AppIndex.App app = apps.byPackage(sc.pkg);
        if (app == null) return null;
        LinearLayout cell = new LinearLayout(act);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(act);
        Drawable custom = sc.icon.isEmpty() ? null : Drawable.createFromPath(sc.icon);
        icon.setImageDrawable(custom != null ? custom : icons.get(app, p));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6), 0);
        cell.addView(icon, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView label = Ui.text(act, sc.label.isEmpty() ? app.label : sc.label, 11, p.text);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setShadowLayer(4f, 0f, 1f, 0xAA000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 4);
        cell.addView(label, lp);
        cell.setOnClickListener(v -> apps.launch(act, app));
        return cell;
    }

    private void makeChrome(PageLayout page, boolean on) {
        for (int i = 0; i < page.getChildCount(); i++) {
            ((ElementView) page.getChildAt(i)).setEditing(on, p.primary);
        }
        page.setGrid(on ? prefs.gridCols() : 0, prefs.gridRows(), Ui.alpha(p.textDim, 0x80));
        page.setPassive(!on && false);
    }

    private void makePeeks() {
        dropPeeks();
        float s = slot();
        if (screens.current > 0) {
            peekPrev = build(screens.current - 1, false);
            root.addView(peekPrev, 0, new FrameLayout.LayoutParams(-1, -1));
            peekPrev.setTranslationX(-s);
        }
        if (screens.current < screens.pages.size() - 1) {
            peekNext = build(screens.current + 1, false);
            root.addView(peekNext, 0, new FrameLayout.LayoutParams(-1, -1));
            peekNext.setTranslationX(s);
        }
    }

    private void dropPeeks() {
        if (peekPrev != null) root.removeView(peekPrev);
        if (peekNext != null) root.removeView(peekNext);
        peekPrev = null;
        peekNext = null;
    }

    private void fade(PageLayout page, boolean in) {
        if (page == null) return;
        page.setAlpha(in ? 0f : 1f);
        page.animate().alpha(in ? 1f : 0f).setDuration(240).setInterpolator(Ui.ease(act)).start();
    }

    private void fadeOutPeeks() {
        PageLayout a = peekPrev;
        PageLayout b = peekNext;
        peekPrev = null;
        peekNext = null;
        for (PageLayout pg : new PageLayout[]{a, b}) {
            if (pg == null) continue;
            pg.animate().alpha(0f).setDuration(200).setInterpolator(Ui.ease(act))
                    .withEndAction(() -> root.removeView(pg)).start();
        }
    }

    private void prepareNeighbor(int want, int dir) {
        dropNeighbor();
        PageLayout adopted = null;
        if (editing) {
            if (want < screens.current && peekPrev != null) {
                adopted = peekPrev;
                peekPrev = null;
            } else if (want > screens.current && peekNext != null) {
                adopted = peekNext;
                peekNext = null;
            }
        }
        if (adopted != null) {
            neighbor = adopted;
        } else {
            neighbor = build(want, true);
            root.addView(neighbor, 0, new FrameLayout.LayoutParams(-1, -1));
        }
        neighborIndex = want;
        swipeDir = dir;
    }

    private void dropNeighbor() {
        if (neighbor != null) {
            if (editing && neighborIndex >= 0) {
                if (neighborIndex < screens.current) {
                    peekPrev = neighbor;
                } else {
                    peekNext = neighbor;
                }
                neighbor.setTranslationX(neighborIndex < screens.current ? -slot() : slot());
            } else {
                root.removeView(neighbor);
            }
        }
        neighbor = null;
        neighborIndex = -1;
    }

    private void settle(boolean commit) {
        PageLayout current = pageView;
        PageLayout other = neighbor;
        PageLayout opp = opposite();
        int newIndex = neighborIndex;
        int dir = swipeDir;
        float from = current.getTranslationX();
        float to = commit ? -dir * swipeW : 0f;
        animating = true;
        current.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (other != null) other.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (opp != null) opp.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(Math.round(140 + 180 * Math.abs(to - from) / swipeW));
        a.setInterpolator(Ui.ease(act));
        a.addUpdateListener(v -> {
            float x = (Float) v.getAnimatedValue();
            current.setTranslationX(x);
            if (other != null) other.setTranslationX(x + dir * swipeW);
            if (opp != null) opp.setTranslationX(x - dir * swipeW);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator x) {
                current.setLayerType(View.LAYER_TYPE_NONE, null);
                if (other != null) other.setLayerType(View.LAYER_TYPE_NONE, null);
                if (opp != null) opp.setLayerType(View.LAYER_TYPE_NONE, null);
                if (commit && other != null) {
                    finishCommit(current, other, opp, newIndex, dir);
                } else {
                    if (other != null) {
                        if (editing) {
                            if (dir > 0) {
                                peekNext = other;
                            } else {
                                peekPrev = other;
                            }
                        } else {
                            root.removeView(other);
                        }
                    }
                    current.setTranslationX(0f);
                }
                neighbor = null;
                neighborIndex = -1;
                animating = false;
                updateDots();
                updateCounter();
                sizeWidgets();
            }
        });
        a.start();
    }

    private void finishCommit(PageLayout current, PageLayout other, PageLayout opp, int newIndex, int dir) {
        pageView = other;
        other.setTranslationX(0f);
        screens.current = newIndex;
        screens.save();
        if (!editing) {
            root.removeView(current);
            return;
        }
        if (opp != null) root.removeView(opp);
        for (int i = 0; i < current.getChildCount(); i++) {
            ((ElementView) current.getChildAt(i)).setEditing(false, 0);
        }
        current.setGrid(0, prefs.gridRows(), 0);
        current.setPassive(true);
        current.setOnLongClickListener(null);
        current.setLongClickable(false);
        makeChrome(other, true);
        other.setPassive(false);
        int far = newIndex + dir;
        PageLayout farPeek = null;
        if (far >= 0 && far < screens.pages.size()) {
            farPeek = build(far, false);
            root.addView(farPeek, 0, new FrameLayout.LayoutParams(-1, -1));
            farPeek.setTranslationX(dir * swipeW);
        }
        if (dir > 0) {
            peekPrev = current;
            peekNext = farPeek;
        } else {
            peekNext = current;
            peekPrev = farPeek;
        }
    }

    private void sizeWidgets() {
        if (pageView == null) return;
        PageLayout page = pageView;
        page.post(() -> {
            DisplayMetrics dm = act.getResources().getDisplayMetrics();
            for (int i = 0; i < page.getChildCount(); i++) {
                ElementView ev = (ElementView) page.getChildAt(i);
                if (!(ev.content() instanceof AppWidgetHostView)) continue;
                int w = Math.round(ev.getWidth() / dm.density);
                int h = Math.round(ev.getHeight() / dm.density);
                ((AppWidgetHostView) ev.content()).updateAppWidgetSize(null, w, h, w, h);
            }
        });
    }

    private void buildToolbar() {
        toolbar.removeAllViews();
        toolbar.setBackground(Ui.round(Ui.alpha(p.base, 0xE6), Ui.dp(act, 28)));
        toolbar.setPadding(Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4));
        toolbar.addView(tool("◀", p.container, p.onContainer, v -> go(-1)));
        counter = Ui.text(act, "", 13, p.text);
        counter.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0);
        toolbar.addView(counter);
        toolbar.addView(tool("▶", p.container, p.onContainer, v -> go(1)));
        toolbar.addView(tool("＋ Widget", p.container, p.onContainer, v -> Pickers.addWidget(act)));
        toolbar.addView(tool("＋ App", p.container, p.onContainer, v -> Pickers.addApp(act)));
        toolbar.addView(tool("Listo", p.primary, p.onPrimary, v -> act.exitEdit()));
    }

    private TextView tool(String label, int bg, int fg, View.OnClickListener l) {
        TextView t = Ui.text(act, label, 13, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));
        t.setBackground(Ui.round(bg, Ui.dp(act, 20)));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(act, 2), 0, Ui.dp(act, 2), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void updateCounter() {
        if (counter != null) counter.setText((screens.current + 1) + "/" + screens.pages.size());
    }

    private void updateDots() {
        dots.removeAllViews();
        int n = screens.pages.size();
        dots.setVisibility(n > 1 && !ime && p != null ? View.VISIBLE : View.GONE);
        if (n < 2 || p == null) return;
        for (int i = 0; i < n; i++) {
            View d = new View(act);
            d.setBackground(Ui.round(i == screens.current ? p.primary : Ui.alpha(p.textDim, 0x90), Ui.dp(act, 4)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 8), Ui.dp(act, 8));
            lp.setMargins(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
            dots.addView(d, lp);
        }
    }
}
