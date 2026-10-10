package com.leexbox.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("leex", Context.MODE_PRIVATE);
    }

    boolean materialYou() { return sp.getBoolean("material_you", true); }
    void materialYou(boolean v) { sp.edit().putBoolean("material_you", v).apply(); }

    int seed() { return sp.getInt("seed", 0xFF7C5CFF); }
    void seed(int v) { sp.edit().putInt("seed", v).apply(); }

    int scrim() { return sp.getInt("scrim", 55); }
    void scrim(int v) { sp.edit().putInt("scrim", v).apply(); }

    boolean showDock() { return sp.getBoolean("show_dock", true); }
    void showDock(boolean v) { sp.edit().putBoolean("show_dock", v).apply(); }

    boolean dockTransparent() { return sp.getBoolean("dock_transparent", false); }
    void dockTransparent(boolean v) { sp.edit().putBoolean("dock_transparent", v).apply(); }

    boolean showStats() { return sp.getBoolean("show_stats", true); }
    void showStats(boolean v) { sp.edit().putBoolean("show_stats", v).apply(); }

    boolean welcome() { return sp.getBoolean("welcome", true); }
    void welcome(boolean v) { sp.edit().putBoolean("welcome", v).apply(); }

    boolean autoKeyboard() { return sp.getBoolean("auto_keyboard", true); }
    void autoKeyboard(boolean v) { sp.edit().putBoolean("auto_keyboard", v).apply(); }

    boolean drawerGrid() { return sp.getBoolean("drawer_grid", false); }
    void drawerGrid(boolean v) { sp.edit().putBoolean("drawer_grid", v).apply(); }

    String drawerAnim() { return sp.getString("drawer_anim", "bottom"); }
    void drawerAnim(String v) { sp.edit().putString("drawer_anim", v).apply(); }

    boolean live() { return false; }
    void live(boolean v) { sp.edit().putBoolean("live", v).apply(); }

    boolean tintFallback() { return sp.getBoolean("tint_fallback", true); }
    void tintFallback(boolean v) { sp.edit().putBoolean("tint_fallback", v).apply(); }

    String iconPack() { return sp.getString("icon_pack", ""); }
    void iconPack(String v) { sp.edit().putString("icon_pack", v).apply(); }

    String layout() { return sp.getString("layout", ""); }
    void layout(String v) { sp.edit().putString("layout", v).apply(); }

    int page() { return sp.getInt("page", 0); }
    void page(int v) { sp.edit().putInt("page", v).apply(); }

    String cwd() { return sp.getString("cwd", TermuxBridge.HOME); }
    void cwd(String v) { sp.edit().putString("cwd", v).apply(); }

    int size(String key, int def) { return sp.getInt("size_" + key, def); }
    void saveSize(String key, int dp) { sp.edit().putInt("size_" + key, dp).apply(); }

    void clearSizes() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) {
            if (k.startsWith("size_")) e.remove(k);
        }
        e.apply();
    }

    String gesture(String key) { return sp.getString(key, Gestures.defaultFor(key)); }
    void gesture(String key, String v) { sp.edit().putString(key, v).apply(); }

    List<String> dock() {
        List<String> d = csv("dock");
        return d.size() > DockModule.MAX ? new ArrayList<String>(d.subList(0, DockModule.MAX)) : d;
    }

    void dock(List<String> v) {
        List<String> d = v.size() > DockModule.MAX ? v.subList(0, DockModule.MAX) : v;
        sp.edit().putString("dock", String.join(",", d)).apply();
    }

    List<Integer> widgets() {
        List<Integer> out = new ArrayList<>();
        for (String s : csv("widgets")) out.add(Integer.parseInt(s));
        return out;
    }

    void widgets(List<Integer> v) {
        List<String> s = new ArrayList<>();
        for (int i : v) s.add(String.valueOf(i));
        sp.edit().putString("widgets", String.join(",", s)).apply();
    }

    Set<String> hidden() { return new LinkedHashSet<String>(csv("hidden")); }
    void hidden(Set<String> v) { sp.edit().putString("hidden", String.join(",", v)).apply(); }

    private List<String> csv(String key) {
        String raw = sp.getString(key, "");
        return raw.isEmpty() ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(raw.split(",")));
    }
}
