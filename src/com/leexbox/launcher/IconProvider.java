package com.leexbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class IconProvider {
    private static final String[] PACK_ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme"
    };

    private final PackageManager pm;
    private final Prefs prefs;
    private final AppIndex apps;
    private final Map<String, String> map = new HashMap<>();
    private Resources packRes;
    private String packPkg = "";

    IconProvider(Context c, Prefs prefs, AppIndex apps) {
        this.pm = c.getPackageManager();
        this.prefs = prefs;
        this.apps = apps;
        load(prefs.iconPack());
    }

    List<String[]> packs() {
        Map<String, String> found = new LinkedHashMap<>();
        for (String action : PACK_ACTIONS) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(action), 0)) {
                found.put(r.activityInfo.packageName, r.loadLabel(pm).toString());
            }
        }
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            out.add(new String[]{e.getKey(), e.getValue()});
        }
        return out;
    }

    void load(String pkg) {
        map.clear();
        packRes = null;
        packPkg = "";
        if (pkg.isEmpty()) return;
        try {
            Resources res = pm.getResourcesForApplication(pkg);
            XmlPullParser xp = openFilter(res, pkg);
            int ev;
            while ((ev = xp.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && xp.getName().equals("item")) {
                    String comp = xp.getAttributeValue(null, "component");
                    String drawable = xp.getAttributeValue(null, "drawable");
                    if (comp == null || drawable == null) continue;
                    int a = comp.indexOf('{');
                    int b = comp.indexOf('}');
                    if (a >= 0 && b > a) map.put(comp.substring(a + 1, b), drawable);
                }
            }
            packRes = res;
            packPkg = pkg;
        } catch (Exception e) {
            map.clear();
        }
    }

    Drawable get(AppIndex.App app, Palette p) {
        Drawable packed = fromPack(app);
        if (packed != null) return packed;

        Drawable d = apps.icon(app);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            Drawable layer = null;
            if (Build.VERSION.SDK_INT >= 33) layer = ad.getMonochrome();
            if (layer == null && prefs.tintFallback()) layer = ad.getForeground();
            if (layer != null) {
                layer = layer.mutate();
                layer.setTint(p.onContainer);
                return new AdaptiveIconDrawable(new ColorDrawable(p.container), layer);
            }
        }
        return d;
    }

    private Drawable fromPack(AppIndex.App app) {
        if (packRes == null) return null;
        String name = map.get(app.pkg + "/" + app.info.activityInfo.name);
        if (name == null) return null;
        try {
            int id = packRes.getIdentifier(name, "drawable", packPkg);
            if (id == 0) id = packRes.getIdentifier(name, "mipmap", packPkg);
            return id == 0 ? null : packRes.getDrawable(id, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private static XmlPullParser openFilter(Resources res, String pkg) throws Exception {
        int id = res.getIdentifier("appfilter", "xml", pkg);
        if (id != 0) return res.getXml(id);
        XmlPullParser xp = Xml.newPullParser();
        xp.setInput(res.getAssets().open("appfilter.xml"), null);
        return xp;
    }
}
