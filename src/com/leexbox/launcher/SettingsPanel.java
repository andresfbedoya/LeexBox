package com.leexbox.launcher;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;

final class SettingsPanel {
    private static final int[] SWATCHES = {
            0xFF7C5CFF, 0xFFFF5C7A, 0xFFFF9F43, 0xFFFFD166, 0xFF3DDC84, 0xFF2ED3C6, 0xFF4DA3FF
    };
    private static final String[][] SECTIONS = {
            {"appearance", "Apariencia", "Colores, Material You y fondo"},
            {"icons", "Íconos", "Íconos del sistema, Material You y packs"},
            {"screen", "Pantalla", "Disposición, cuadrícula, widgets y pantallas"},
            {"dock", "Dock", "Visibilidad y transparencia"},
            {"drawer", "Cajón de apps", "Lista o cuadrícula, animación y apps ocultas"},
            {"terminal", "Terminal y gestos", "Texto de inicio, teclado y gestos"},
            {"system", "Sistema", "Fondo, launcher y reinicio"}
    };

    private final HomeActivity act;
    private final Prefs prefs;
    private final FrameLayout host;
    private FrameLayout overlay;
    private FrameLayout shell;
    private ScrollView scroll;
    private String section;
    private Palette p;
    private int insetL;
    private int insetT;
    private int insetR;
    private int insetB;

    SettingsPanel(HomeActivity act, Prefs prefs, FrameLayout host) {
        this.act = act;
        this.prefs = prefs;
        this.host = host;
    }

    boolean isShown() {
        return overlay != null;
    }

    void setInsets(int l, int t, int r, int b) {
        insetL = l;
        insetT = t;
        insetR = r;
        insetB = b;
        if (shell != null) shell.setPadding(l, t, r, b);
    }

    void apply(Palette palette) {
        p = palette;
        if (overlay == null) return;
        int y = scroll.getScrollY();
        shell.removeAllViews();
        build(false);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    void show() {
        if (overlay != null || p == null) return;
        section = null;
        FrameLayout o = new FrameLayout(act);
        overlay = o;
        o.setClickable(true);
        shell = new FrameLayout(act);
        o.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        build(false);
        host.addView(o, new FrameLayout.LayoutParams(-1, -1));
        o.setAlpha(0f);
        o.post(() -> {
            o.setTranslationY(Ui.dp(act, 32));
            o.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(Ui.ease(act)).withLayer().start();
        });
    }

    void hide() {
        FrameLayout o = overlay;
        if (o == null) return;
        overlay = null;
        shell = null;
        scroll = null;
        o.animate().alpha(0f).translationY(Ui.dp(act, 24)).setDuration(180)
                .setInterpolator(Ui.ease(act)).withLayer()
                .withEndAction(() -> host.removeView(o)).start();
    }

    void back() {
        if (overlay == null) return;
        if (section != null) {
            section = null;
            shell.removeAllViews();
            build(true);
        } else {
            hide();
        }
    }

    private void open(String name) {
        section = name;
        shell.removeAllViews();
        build(true);
    }

    private void build(boolean animate) {
        overlay.setBackgroundColor(Ui.alpha(p.base, 0xF5));
        shell.setPadding(insetL, insetT, insetR, insetB);
        int pad = Ui.dp(act, 20);

        LinearLayout column = new LinearLayout(act);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, pad, pad, Ui.dp(act, 8));
        String title = "Ajustes";
        if (section != null) {
            for (String[] s : SECTIONS) {
                if (s[0].equals(section)) title = s[1];
            }
            TextView backArrow = Ui.text(act, "←", 24, p.text);
            backArrow.setPadding(0, 0, Ui.dp(act, 16), 0);
            backArrow.setOnClickListener(v -> back());
            head.addView(backArrow);
        }
        TextView close = Ui.text(act, "✕", 22, p.textDim);
        close.setPadding(pad, 0, 0, 0);
        close.setOnClickListener(v -> hide());
        head.addView(Ui.text(act, title, 26, p.text), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(close);
        column.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(act, 32));

        if (section == null) {
            mainMenu(body);
        } else {
            switch (section) {
                case "appearance": appearance(body); break;
                case "icons": icons(body); break;
                case "screen": screen(body); break;
                case "dock": dock(body); break;
                case "drawer": drawer(body); break;
                case "terminal": terminal(body); break;
                default: system(body); break;
            }
        }

        scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(column, new FrameLayout.LayoutParams(-1, -1));
        if (animate) {
            column.setAlpha(0f);
            column.setTranslationX(Ui.dp(act, 28));
            column.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(Ui.ease(act)).start();
        }
    }

    private void mainMenu(LinearLayout body) {
        for (String[] s : SECTIONS) {
            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(act, 20), Ui.dp(act, 16), Ui.dp(act, 20), Ui.dp(act, 16));
            card.setBackground(Ui.round(p.container, Ui.dp(act, 24)));
            card.addView(Ui.text(act, s[1], 17, p.onContainer));
            TextView sub = Ui.text(act, s[2], 12, Ui.alpha(p.onContainer, 0xB0));
            sub.setPadding(0, Ui.dp(act, 2), 0, 0);
            card.addView(sub);
            card.setOnClickListener(v -> open(s[0]));
            add(body, card, 10);
        }
        TextView credit = Ui.text(act, "Created by Andres F Bedoya under the name of Leex.", 11, p.textDim);
        credit.setGravity(Gravity.CENTER);
        add(body, credit, 32);
    }

    private void appearance(LinearLayout body) {
        add(body, toggle("Colores del sistema (Material You)", prefs.materialYou(), on -> {
            prefs.materialYou(on);
            act.applyTheme();
        }), 4);
        add(body, Ui.text(act, "Color personalizado", 13, p.textDim), 12);
        add(body, swatches(), 8);
        add(body, hexField(), 4);
        add(body, Ui.text(act, "Opacidad del fondo", 13, p.textDim), 16);
        add(body, opacity(), 4);
    }

    private void icons(LinearLayout body) {
        add(body, toggle("Íconos Material You (monocromáticos)", prefs.themedIcons(), on -> {
            prefs.themedIcons(on);
            act.refreshIcons();
            act.refreshSettings();
        }), 4);
        if (prefs.themedIcons()) {
            add(body, toggle("Teñir también los íconos sin capa monocromática", prefs.tintFallback(), on -> {
                prefs.tintFallback(on);
                act.refreshIcons();
            }), 0);
        }
        add(body, pill("Pack de íconos…", false, v -> Pickers.iconPack(act)), 12);
    }

    private void screen(LinearLayout body) {
        add(body, pill("Editar disposición (mover y cambiar tamaño)", true, v -> {
            hide();
            act.enterEdit();
        }), 4);
        add(body, pill("Cuadrícula: " + Pickers.gridLabel(prefs), false, v -> Pickers.grid(act)), 8);
        add(body, pill("Añadir widget…", false, v -> Pickers.addWidget(act)), 8);
        add(body, pill("Añadir app a esta pantalla…", false, v -> Pickers.addApp(act)), 8);
        add(body, pill("Añadir LeexScripts Mini", false, v -> act.addScripts()), 8);
        add(body, pill("Pantallas…", false, v -> Pickers.screens(act)), 8);
        add(body, toggle("Barra CPU / RAM / temp", prefs.showStats(), on -> act.setStats(on)), 12);
    }

    private void dock(LinearLayout body) {
        add(body, toggle("Mostrar dock", prefs.showDock(), on -> {
            prefs.showDock(on);
            act.updateModules();
        }), 4);
        add(body, toggle("Dock transparente", prefs.dockTransparent(), on -> {
            prefs.dockTransparent(on);
            act.refreshDock();
        }), 0);
    }

    private void drawer(LinearLayout body) {
        add(body, toggle("Mostrar en cuadrícula", prefs.drawerGrid(), prefs::drawerGrid), 4);
        add(body, pill("Animación: " + Pickers.drawerAnimLabel(prefs.drawerAnim()), false,
                v -> Pickers.drawerAnim(act)), 12);
        add(body, pill("Apps ocultas…", false, v -> Pickers.hidden(act)), 8);
    }

    private void terminal(LinearLayout body) {
        add(body, toggle("Quitar el texto de inicio", !prefs.welcome(), on -> prefs.welcome(!on)), 4);
        add(body, toggle("Teclado manual (no abrirlo al tocar la terminal)", !prefs.autoKeyboard(),
                on -> prefs.autoKeyboard(!on)), 0);
        add(body, pill("Gestos…", false, v -> Pickers.gestures(act)), 12);
    }

    private void system(LinearLayout body) {
        add(body, pill("Cambiar fondo de pantalla", false, v -> {
            hide();
            act.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), null));
        }), 4);
        if (!act.isDefaultHome()) {
            add(body, pill("Usar como launcher del sistema", false, v -> {
                hide();
                act.requestHome();
            }), 8);
        }
        add(body, pill("Reiniciar launcher", false, v -> {
            hide();
            act.restartLauncher();
        }), 8);
    }

    private TextView pill(String label, boolean primary, View.OnClickListener l) {
        TextView t = Ui.pill(act, label, primary ? p.primary : p.container, primary ? p.onPrimary : p.onContainer, l);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return t;
    }

    private void add(LinearLayout body, View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, topDp);
        body.addView(v, lp);
    }

    private Switch toggle(String label, boolean on, Consumer<Boolean> change) {
        Switch s = new Switch(act);
        s.setText(label);
        s.setTextSize(15);
        s.setTextColor(p.text);
        s.setChecked(on);
        s.setThumbTintList(Ui.checked(p.primary, p.textDim));
        s.setTrackTintList(Ui.checked(Ui.alpha(p.primary, 0x80), Ui.alpha(p.textDim, 0x50)));
        s.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        s.setOnCheckedChangeListener((b, v) -> change.accept(v));
        return s;
    }

    private LinearLayout swatches() {
        LinearLayout row = new LinearLayout(act);
        for (int c : SWATCHES) {
            View s = new View(act);
            s.setBackground(Ui.round(c, Ui.dp(act, 16)));
            s.setOnClickListener(v -> {
                prefs.seed(c);
                prefs.materialYou(false);
                act.applyTheme();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 32), Ui.dp(act, 32));
            lp.rightMargin = Ui.dp(act, 10);
            row.addView(s, lp);
        }
        return row;
    }

    private EditText hexField() {
        EditText e = new EditText(act);
        e.setHint("#RRGGBB");
        e.setTextSize(14);
        e.setTextColor(p.text);
        e.setHintTextColor(p.textDim);
        e.setBackgroundTintList(ColorStateList.valueOf(p.textDim));
        e.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener((v, id, ev) -> {
            String s = v.getText().toString().trim();
            try {
                prefs.seed(Color.parseColor(s.startsWith("#") ? s : "#" + s) | 0xFF000000);
                prefs.materialYou(false);
                act.applyTheme();
            } catch (IllegalArgumentException ex) {
                v.setError("Hex inválido");
            }
            return true;
        });
        return e;
    }

    private SeekBar opacity() {
        SeekBar sb = new SeekBar(act);
        sb.setMax(100);
        sb.setProgress(prefs.scrim());
        sb.setProgressTintList(ColorStateList.valueOf(p.primary));
        sb.setThumbTintList(ColorStateList.valueOf(p.primary));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                act.setScrim(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }
}
