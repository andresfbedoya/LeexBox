package com.leexbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppIndex {
    static final class App {
        final String label;
        final String pkg;
        final ResolveInfo info;

        App(String label, String pkg, ResolveInfo info) {
            this.label = label;
            this.pkg = pkg;
            this.info = info;
        }
    }

    private final PackageManager pm;
    private final Prefs prefs;
    private List<App> apps = new ArrayList<>();

    AppIndex(Context c, Prefs prefs) {
        this.pm = c.getPackageManager();
        this.prefs = prefs;
        refresh();
    }

    void refresh() {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> next = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            next.add(new App(r.loadLabel(pm).toString(), r.activityInfo.packageName, r));
        }
        next.sort((x, y) -> norm(x.label).compareTo(norm(y.label)));
        apps = next;
    }

    List<App> all() {
        return apps;
    }

    List<App> visible() {
        Set<String> hidden = prefs.hidden();
        List<App> out = new ArrayList<>();
        for (App a : apps) {
            if (!hidden.contains(a.pkg)) out.add(a);
        }
        return out;
    }

    App byPackage(String pkg) {
        for (App a : apps) {
            if (a.pkg.equals(pkg)) return a;
        }
        return null;
    }

    App find(String query) {
        return find(query, false);
    }

    App find(String query, boolean includeHidden) {
        String q = norm(query);
        if (q.isEmpty()) return null;
        for (App a : apps) {
            if (norm(a.label).equals(q)) return a;
        }
        if (q.length() < 3 || q.contains(" ")) return null;
        Set<String> hidden = prefs.hidden();
        App hit = null;
        for (App a : apps) {
            if (!includeHidden && hidden.contains(a.pkg)) continue;
            if (norm(a.label).startsWith(q)) {
                if (hit != null && !hit.pkg.equals(a.pkg)) return null;
                hit = a;
            }
        }
        return hit;
    }

    void launch(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.info.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        c.startActivity(i);
    }

    Drawable icon(App a) {
        return a.info.loadIcon(pm);
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
