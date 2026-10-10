package com.leexbox.launcher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

public class HomeActivity extends Activity {
    private static final int REQ_BIND = 10;
    private static final int REQ_CONFIG = 11;
    private static final int REQ_HOME = 12;
    private static final int REQ_RECONFIG = 13;
    private static final int MODE_NONE = 0;
    private static final int MODE_DRAWER = 1;
    private static final int MODE_PAGE = 2;

    private Prefs prefs;
    private AppIndex apps;
    private IconProvider icons;
    private Gestures gestures;
    private Palette palette;
    private View scrim;
    private FrameLayout content;
    private TerminalModule terminal;
    private DockModule dock;
    private StatsModule stats;
    private WidgetsModule widgets;
    private Pager pager;
    private AppDrawer drawer;
    private SettingsPanel settings;
    private boolean imeVisible;
    private int pendingWidget = -1;
    private AppWidgetProviderInfo pendingInfo;
    private VelocityTracker velocity;
    private int dragMode;
    private boolean drawerCandidate;
    private boolean pageCandidate;
    private float downRawX;
    private float downRawY;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);

        prefs = new Prefs(this);
        apps = new AppIndex(this, prefs);
        icons = new IconProvider(this, prefs, apps);
        gestures = new Gestures(this);

        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        content = new FrameLayout(this);
        FrameLayout overlays = new FrameLayout(this);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlays, new FrameLayout.LayoutParams(-1, -1));

        terminal = new TerminalModule(this, prefs, apps);
        dock = new DockModule(this, prefs, apps, icons);
        stats = new StatsModule(this);
        widgets = new WidgetsModule(this);
        pager = new Pager(this, prefs, apps, icons, terminal, stats, widgets);
        settings = new SettingsPanel(this, prefs, overlays);
        drawer = new AppDrawer(this, prefs, apps, icons, content);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(pager.view(), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-2, -2);
        dotsLp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(pager.dotsView(), dotsLp);

        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(-2, -2);
        dockLp.gravity = Gravity.CENTER_HORIZONTAL;
        dockLp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        column.addView(dock.view(), dockLp);
        content.addView(column, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = in.getInsets(WindowInsets.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            content.setPadding(bars.left, bars.top, bars.right, bottom);
            settings.setInsets(bars.left, bars.top, bars.right, bottom);
            imeVisible = in.isVisible(WindowInsets.Type.ime());
            updateModules();
            return WindowInsets.CONSUMED;
        });

        setContentView(root);
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        applyTheme();
        terminal.welcome();
    }

    @Override
    protected void onStart() {
        super.onStart();
        widgets.start();
    }

    @Override
    protected void onStop() {
        widgets.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        apps.refresh();
        dock.refresh(palette);
        stats.start();
        if (!prefs.autoKeyboard()) {
            terminal.clearFocus();
            content.requestFocus();
            hideKeyboard();
        }
    }

    @Override
    protected void onPause() {
        stats.stop();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        settings.hide();
        drawer.close();
        exitEdit();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        applyTheme();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isShown()) {
            drawer.close();
        } else if (settings.isShown()) {
            settings.hide();
        } else if (pager.isEditing()) {
            exitEdit();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (dragTouch(e)) return true;
        String key = gestures.feed(e);
        if (key != null && !settings.isShown() && !drawer.isShown() && allowed(key)) {
            content.post(() -> runAction(prefs.gesture(key)));
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        terminal.say(ok
                ? "Permiso concedido. Repite el comando."
                : "Permiso denegado. Actívalo en Ajustes > Apps > LeexBox > Permisos > Permisos adicionales.", !ok);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_RECONFIG) {
            pager.reload();
            return;
        }
        if ((request != REQ_BIND && request != REQ_CONFIG) || pendingWidget < 0) return;
        if (result != RESULT_OK) {
            widgets.delete(pendingWidget);
            pendingWidget = -1;
        } else if (request == REQ_BIND) {
            configureNew();
        } else {
            finishWidget();
        }
    }

    Prefs prefs() { return prefs; }
    AppIndex apps() { return apps; }
    IconProvider icons() { return icons; }
    Palette palette() { return palette; }
    int pageCount() { return pager.pageCount(); }
    int pageIndex() { return pager.pageIndex(); }

    void applyTheme() {
        palette = Palette.resolve(this, prefs.materialYou(), prefs.seed());
        scrim.setBackgroundColor(palette.scrim(prefs.scrim()));
        terminal.apply(palette);
        stats.apply(palette);
        dock.refresh(palette);
        pager.apply(palette);
        settings.apply(palette);
        drawer.apply(palette);
        updateModules();
    }

    void refreshSettings() {
        settings.apply(palette);
    }

    void setScrim(int percent) {
        prefs.scrim(percent);
        scrim.setBackgroundColor(palette.scrim(percent));
    }

    void updateModules() {
        dock.setVisible(prefs.showDock() && !imeVisible);
        pager.setIme(imeVisible);
    }

    void refreshDock() {
        dock.refresh(palette);
    }

    void setIconPack(String pkg) {
        prefs.iconPack(pkg);
        icons.load(pkg);
        dock.refresh(palette);
        pager.reload();
        drawer.apply(palette);
    }

    void setStats(boolean on) {
        prefs.showStats(on);
        pager.ensureStats(on);
    }

    void showSettings() {
        if (pager.isEditing()) return;
        hideKeyboard();
        settings.show();
    }

    void showDrawer() {
        hideKeyboard();
        drawer.open();
    }

    void enterEdit() {
        hideKeyboard();
        settings.hide();
        drawer.close();
        pager.setEditing(true);
    }

    void exitEdit() {
        if (pager.isEditing()) pager.setEditing(false);
    }

    void addScreen() {
        settings.hide();
        pager.addScreen();
    }

    void removeScreen() {
        settings.hide();
        for (int id : pager.removeScreen()) widgets.delete(id);
    }

    void resetLayout() {
        settings.hide();
        for (int id : pager.resetLayout()) widgets.delete(id);
    }

    void addApp(String pkg) {
        settings.hide();
        pager.addApp(pkg);
    }

    void restartLauncher() {
        TermuxBridge.reset();
        prefs.cwd(TermuxBridge.HOME);
        recreate();
    }

    void addWidget(AppWidgetProviderInfo info) {
        settings.hide();
        pendingWidget = widgets.allocate();
        pendingInfo = info;
        if (widgets.manager().bindAppWidgetIdIfAllowed(pendingWidget, info.provider)) {
            configureNew();
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(i, REQ_BIND);
        }
    }

    void deleteWidgetId(int id) {
        widgets.delete(id);
    }

    void configureWidget(int id) {
        AppWidgetProviderInfo info = widgets.manager().getAppWidgetInfo(id);
        if (info == null || info.configure == null) {
            Toast.makeText(this, "Este widget no tiene configuración", Toast.LENGTH_SHORT).show();
            return;
        }
        widgets.host().startAppWidgetConfigureActivityForResult(this, id, 0, REQ_RECONFIG, null);
    }

    boolean isDefaultHome() {
        RoleManager rm = getSystemService(RoleManager.class);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME);
    }

    void requestHome() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME);
        } else {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        }
    }

    private void configureNew() {
        if (pendingInfo.configure != null) {
            widgets.host().startAppWidgetConfigureActivityForResult(this, pendingWidget, 0, REQ_CONFIG, null);
        } else {
            finishWidget();
        }
    }

    private void finishWidget() {
        pager.addWidget(pendingWidget, pendingInfo);
        pendingWidget = -1;
    }

    private void runAction(String action) {
        if (action.startsWith("app:")) {
            AppIndex.App app = apps.byPackage(action.substring(4));
            if (app != null) apps.launch(this, app);
        } else if (action.startsWith("cmd:")) {
            terminal.execute(action.substring(4));
        } else if (action.equals("drawer")) {
            showDrawer();
        } else if (action.equals("clear")) {
            terminal.clear();
        } else if (action.equals("restart")) {
            restartLauncher();
        }
    }

    private boolean allowed(String key) {
        if (pager.isEditing()) return false;
        if (key.charAt(1) == '2') return true;
        float x = gestures.startX();
        float y = gestures.startY();
        if (dock.hit(x, y) || pager.hitWidget(x, y)) return false;
        boolean vertical = key.endsWith("up") || key.endsWith("down");
        return !(vertical && terminal.hit(x, y) && terminal.scrollable());
    }

    private boolean dragTouch(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (velocity != null) velocity.recycle();
            velocity = VelocityTracker.obtain();
            downRawX = e.getRawX();
            downRawY = e.getRawY();
            dragMode = MODE_NONE;
            boolean free = !pager.isEditing() && !settings.isShown() && !drawer.isShown();
            boolean blocked = dock.hit(downRawX, downRawY) || pager.hitWidget(downRawX, downRawY);
            drawerCandidate = free && !blocked
                    && prefs.gesture("g1_up").equals("drawer")
                    && prefs.drawerAnim().equals("bottom")
                    && !(terminal.hit(downRawX, downRawY) && terminal.scrollable());
            pageCandidate = free && !blocked && pager.pageCount() > 1;
        }
        if (velocity != null) velocity.addMovement(e);
        if (dragMode == MODE_NONE && !drawerCandidate && !pageCandidate) return false;
        float dx = e.getRawX() - downRawX;
        float dy = e.getRawY() - downRawY;
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (dragMode == MODE_NONE) {
                    int slop = ViewConfiguration.get(this).getScaledTouchSlop() * 2;
                    if (drawerCandidate && -dy > slop && -dy > Math.abs(dx) * 1.5f) {
                        startDrag(e, MODE_DRAWER);
                    } else if (pageCandidate && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        startDrag(e, MODE_PAGE);
                    } else {
                        return false;
                    }
                }
                if (dragMode == MODE_DRAWER) {
                    drawer.dragOpen(-dy);
                } else {
                    pager.swipeBy(dx);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int mode = dragMode;
                dragMode = MODE_NONE;
                drawerCandidate = false;
                pageCandidate = false;
                if (mode == MODE_NONE) return false;
                velocity.computeCurrentVelocity(1000);
                if (mode == MODE_DRAWER) {
                    drawer.endDrag(velocity.getYVelocity());
                } else {
                    pager.endSwipe(velocity.getXVelocity());
                }
                return true;
            }
            default:
                return dragMode != MODE_NONE;
        }
    }

    private void startDrag(MotionEvent e, int mode) {
        dragMode = mode;
        gestures.cancel();
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        hideKeyboard();
        if (mode == MODE_DRAWER) {
            drawer.beginDrag();
        } else {
            pager.beginSwipe();
        }
    }

    private void hideKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(content.getWindowToken(), 0);
    }
}
