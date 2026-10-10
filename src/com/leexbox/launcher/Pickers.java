package com.leexbox.launcher;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Pickers {
    private Pickers() {}

    static void addWidget(HomeActivity act) {
        PackageManager pm = act.getPackageManager();
        List<AppWidgetProviderInfo> list = new ArrayList<>(AppWidgetManager.getInstance(act).getInstalledProviders());
        list.sort((a, b) -> a.loadLabel(pm).compareToIgnoreCase(b.loadLabel(pm)));
        Palette p = act.palette();
        float density = act.getResources().getDisplayMetrics().density;
        int dpi = act.getResources().getDisplayMetrics().densityDpi;

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return list.size();
            }

            @Override
            public Object getItem(int i) {
                return list.get(i);
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
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 8));
                    ImageView thumb = new ImageView(act);
                    thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    thumb.setBackground(Ui.round(p.surface, Ui.dp(act, 12)));
                    thumb.setPadding(Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6));
                    row.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(act, 112), Ui.dp(act, 76)));
                    LinearLayout texts = new LinearLayout(act);
                    texts.setOrientation(LinearLayout.VERTICAL);
                    texts.addView(Ui.text(act, "", 15, p.text));
                    texts.addView(Ui.text(act, "", 11, p.textDim));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                    lp.leftMargin = Ui.dp(act, 14);
                    row.addView(texts, lp);
                } else {
                    row = (LinearLayout) convert;
                }
                AppWidgetProviderInfo info = list.get(i);
                Drawable preview = info.loadPreviewImage(act, dpi);
                if (preview == null) preview = info.loadIcon(act, dpi);
                ((ImageView) row.getChildAt(0)).setImageDrawable(preview);
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                ((TextView) texts.getChildAt(0)).setText(info.loadLabel(pm));
                ((TextView) texts.getChildAt(1)).setText(info.provider.getPackageName() + " · "
                        + Math.round(info.minWidth / density) + "×" + Math.round(info.minHeight / density) + " dp");
                return row;
            }
        };

        new AlertDialog.Builder(act)
                .setTitle("Añadir widget")
                .setAdapter(adapter, (d, i) -> act.addWidget(list.get(i)))
                .show();
    }

    static void addApp(HomeActivity act) {
        List<AppIndex.App> list = act.apps().visible();
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).label;
        new AlertDialog.Builder(act)
                .setTitle("Añadir app a esta pantalla")
                .setItems(names, (d, i) -> act.addApp(list.get(i).pkg))
                .show();
    }

    static void screens(HomeActivity act) {
        new AlertDialog.Builder(act)
                .setTitle("Pantallas (" + (act.pageIndex() + 1) + " de " + act.pageCount() + ")")
                .setItems(new String[]{"＋ Añadir pantalla vacía", "✕ Quitar la pantalla actual",
                        "Restablecer toda la disposición"}, (d, i) -> {
                    if (i == 0) {
                        act.addScreen();
                    } else if (i == 1) {
                        confirm(act, "¿Quitar esta pantalla?",
                                "Se borrarán sus widgets y accesos. La terminal y la barra de stats pasan a la primera pantalla.",
                                act::removeScreen);
                    } else {
                        confirm(act, "¿Restablecer la disposición?",
                                "Vuelve a una sola pantalla con la terminal y la barra, y se quitan widgets y accesos.",
                                act::resetLayout);
                    }
                })
                .show();
    }

    private static void confirm(HomeActivity act, String title, String message, Runnable yes) {
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Sí", (d, w) -> yes.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void drawerAnim(HomeActivity act) {
        String[] labels = {"Desde abajo", "Desde arriba", "Desde la izquierda", "Desde la derecha"};
        String[] values = {"bottom", "top", "left", "right"};
        new AlertDialog.Builder(act)
                .setTitle("Animación del cajón")
                .setItems(labels, (d, i) -> {
                    act.prefs().drawerAnim(values[i]);
                    act.refreshSettings();
                })
                .show();
    }

    static String drawerAnimLabel(String value) {
        switch (value) {
            case "top": return "Desde arriba";
            case "left": return "Desde la izquierda";
            case "right": return "Desde la derecha";
            default: return "Desde abajo";
        }
    }

    static void iconPack(HomeActivity act) {
        List<String[]> packs = act.icons().packs();
        if (packs.isEmpty()) {
            new AlertDialog.Builder(act)
                    .setTitle("Pack de íconos")
                    .setMessage("No hay packs compatibles instalados (formato Nova / ADW).")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] names = new String[packs.size() + 1];
        names[0] = "Predeterminado";
        for (int i = 0; i < packs.size(); i++) names[i + 1] = packs.get(i)[1];
        new AlertDialog.Builder(act)
                .setTitle("Pack de íconos")
                .setItems(names, (d, i) -> act.setIconPack(i == 0 ? "" : packs.get(i - 1)[0]))
                .show();
    }

    static void gestures(HomeActivity act) {
        String[] items = new String[Gestures.KEYS.length];
        for (int i = 0; i < items.length; i++) {
            String key = Gestures.KEYS[i];
            items[i] = Gestures.label(key) + "  →  " + Gestures.describe(act.prefs(), act.apps(), key);
        }
        new AlertDialog.Builder(act)
                .setTitle("Gestos")
                .setItems(items, (d, i) -> action(act, Gestures.KEYS[i]))
                .show();
    }

    private static void action(HomeActivity act, String key) {
        String[] labels = {"Nada", "Cajón de apps", "Limpiar terminal", "Reiniciar launcher",
                "Abrir app…", "Comando de Termux…"};
        String[] values = {"none", "drawer", "clear", "restart", "app", "cmd"};
        new AlertDialog.Builder(act)
                .setTitle(Gestures.label(key))
                .setItems(labels, (d, i) -> {
                    if (values[i].equals("app")) {
                        pickApp(act, key);
                    } else if (values[i].equals("cmd")) {
                        askCommand(act, key);
                    } else {
                        act.prefs().gesture(key, values[i]);
                        Toast.makeText(act, "Guardado", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static void pickApp(HomeActivity act, String key) {
        List<AppIndex.App> list = act.apps().visible();
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).label;
        new AlertDialog.Builder(act)
                .setTitle("Abrir app")
                .setItems(names, (d, i) -> {
                    act.prefs().gesture(key, "app:" + list.get(i).pkg);
                    Toast.makeText(act, "Guardado", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static void askCommand(HomeActivity act, String key) {
        EditText input = new EditText(act);
        input.setHint("ls -la");
        new AlertDialog.Builder(act)
                .setTitle("Comando de Termux")
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    String cmd = input.getText().toString().trim();
                    act.prefs().gesture(key, cmd.isEmpty() ? "none" : "cmd:" + cmd);
                    Toast.makeText(act, "Guardado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void hidden(HomeActivity act) {
        List<AppIndex.App> all = act.apps().all();
        Set<String> hidden = act.prefs().hidden();
        String[] names = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = all.get(i).label;
            checked[i] = hidden.contains(all.get(i).pkg);
        }
        new AlertDialog.Builder(act)
                .setTitle("Apps ocultas")
                .setMultiChoiceItems(names, checked, (d, i, on) -> checked[i] = on)
                .setPositiveButton("Guardar", (d, w) -> {
                    Set<String> out = new LinkedHashSet<>();
                    for (int i = 0; i < all.size(); i++) {
                        if (checked[i]) out.add(all.get(i).pkg);
                    }
                    act.prefs().hidden(out);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
