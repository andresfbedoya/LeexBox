package com.leexbox.launcher;

final class Shortcut {
    final String pkg;
    final String label;
    final String icon;

    Shortcut(String pkg, String label, String icon) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
    }

    static Shortcut parse(String data) {
        String[] parts = data.split("\n", -1);
        return new Shortcut(parts[0], parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
    }

    String encode() {
        return pkg + "\n" + label + "\n" + icon;
    }

    Shortcut withLabel(String value) {
        return new Shortcut(pkg, value, icon);
    }

    Shortcut withIcon(String value) {
        return new Shortcut(pkg, label, value);
    }
}
