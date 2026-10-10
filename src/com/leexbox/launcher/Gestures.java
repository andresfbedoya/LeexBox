package com.leexbox.launcher;

import android.content.Context;
import android.view.MotionEvent;

final class Gestures {
    static final String[] KEYS = {
            "g1_up", "g1_down", "g2_up", "g2_down", "g2_left", "g2_right"
    };

    private final int threshold;
    private float sx, sy, lx, ly, rawX, rawY;
    private int fingers;
    private boolean armed;

    Gestures(Context c) {
        threshold = Ui.dp(c, 72);
    }

    void cancel() {
        armed = false;
    }

    float startX() { return rawX; }
    float startY() { return rawY; }

    String feed(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fingers = 1;
                armed = true;
                sx = lx = e.getX();
                sy = ly = e.getY();
                rawX = e.getRawX();
                rawY = e.getRawY();
                return null;
            case MotionEvent.ACTION_POINTER_DOWN:
                fingers = Math.max(fingers, e.getPointerCount());
                sx = lx = cx(e);
                sy = ly = cy(e);
                return null;
            case MotionEvent.ACTION_MOVE:
                lx = cx(e);
                ly = cy(e);
                return null;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                if (!armed) return null;
                armed = false;
                return resolve();
            case MotionEvent.ACTION_CANCEL:
                armed = false;
                return null;
            default:
                return null;
        }
    }

    private String resolve() {
        if (fingers > 2) return null;
        float dx = lx - sx;
        float dy = ly - sy;
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        if (Math.max(ax, ay) < threshold) return null;
        String dir;
        if (ay > ax * 1.5f) {
            dir = dy < 0 ? "up" : "down";
        } else if (ax > ay * 1.5f) {
            dir = dx < 0 ? "left" : "right";
        } else {
            return null;
        }
        return "g" + fingers + "_" + dir;
    }

    private static float cx(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getX(i);
        return s / e.getPointerCount();
    }

    private static float cy(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getY(i);
        return s / e.getPointerCount();
    }

    static String defaultFor(String key) {
        if (key.equals("g1_up")) return "drawer";
        return "none";
    }

    static String label(String key) {
        String dir = key.substring(3);
        String arrow = dir.equals("up") ? "↑" : dir.equals("down") ? "↓" : dir.equals("left") ? "←" : "→";
        return key.charAt(1) + (key.charAt(1) == '1' ? " dedo " : " dedos ") + arrow;
    }

    static String describe(Prefs prefs, AppIndex apps, String key) {
        String v = prefs.gesture(key);
        if (v.startsWith("app:")) {
            AppIndex.App a = apps.byPackage(v.substring(4));
            return "Abrir " + (a == null ? v.substring(4) : a.label);
        }
        if (v.startsWith("cmd:")) return "Termux: " + v.substring(4);
        switch (v) {
            case "drawer": return "Cajón de apps";
            case "clear": return "Limpiar terminal";
            case "restart": return "Reiniciar launcher";
            default: return "Nada";
        }
    }
}
