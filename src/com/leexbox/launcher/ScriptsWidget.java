package com.leexbox.launcher;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ScriptsWidget {
    private static final class Entry {
        String name;
        String commands;

        Entry(String name, String commands) {
            this.name = name;
            this.commands = commands;
        }
    }

    private final HomeActivity act;
    private final Screens.Item item;
    private final Palette p;
    private final List<Entry> entries = new ArrayList<>();

    ScriptsWidget(HomeActivity act, Screens.Item item, Palette p) {
        this.act = act;
        this.item = item;
        this.p = p;
        load();
    }

    View view() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(Ui.alpha(p.base, 0xCC), Ui.dp(act, 22)));
        root.setPadding(Ui.dp(act, 14), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10));
        root.setOnLongClickListener(v -> {
            act.enterEdit();
            return true;
        });

        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(act, "LeexScripts Mini", 13, p.primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setOnLongClickListener(v -> {
            act.enterEdit();
            return true;
        });
        TextView add = Ui.text(act, "＋", 20, p.primary);
        add.setPadding(Ui.dp(act, 12), 0, 0, 0);
        add.setOnClickListener(v -> editor(null));
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(add);
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        if (entries.isEmpty()) {
            TextView hint = Ui.text(act, "Toca ＋ para crear un lanzador", 12, p.textDim);
            hint.setPadding(0, Ui.dp(act, 8), 0, 0);
            rows.addView(hint);
        }
        for (Entry e : entries) rows.addView(row(e));
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(rows);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private View row(Entry e) {
        LinearLayout r = new LinearLayout(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));
        TextView name = Ui.text(act, e.name, 14, p.text);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(chip("Ejecutar", p.primary, p.onPrimary, () -> act.runScript(e.name, e.commands, false)));
        r.addView(chip("2.º plano", p.container, p.onContainer, () -> act.runScript(e.name, e.commands, true)));
        r.setOnLongClickListener(v -> {
            menu(e);
            return true;
        });
        return r;
    }

    private TextView chip(String label, int bg, int fg, Runnable action) {
        TextView t = Ui.text(act, label, 12, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(act, 12), Ui.dp(act, 7), Ui.dp(act, 12), Ui.dp(act, 7));
        t.setBackground(Ui.round(bg, Ui.dp(act, 16)));
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(act, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private void menu(Entry e) {
        new AlertDialog.Builder(act)
                .setTitle(e.name)
                .setItems(new String[]{"Editar", "Eliminar"}, (d, i) -> {
                    if (i == 0) {
                        editor(e);
                    } else {
                        entries.remove(e);
                        save();
                    }
                })
                .show();
    }

    private void editor(Entry existing) {
        int pad = Ui.dp(act, 20);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(act, 8), pad, 0);
        EditText name = new EditText(act);
        name.setHint("Nombre del lanzador");
        name.setSingleLine(true);
        EditText cmds = new EditText(act);
        cmds.setHint("Comandos (uno por línea)");
        cmds.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cmds.setMinLines(4);
        cmds.setGravity(Gravity.TOP);
        cmds.setTypeface(Typeface.MONOSPACE);
        if (existing != null) {
            name.setText(existing.name);
            cmds.setText(existing.commands);
        }
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        box.addView(cmds, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(act)
                .setTitle(existing == null ? "Nuevo lanzador" : "Editar lanzador")
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String c = cmds.getText().toString().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        Toast.makeText(act, "Pon un nombre y al menos un comando", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (existing == null) {
                        entries.add(new Entry(n, c));
                    } else {
                        existing.name = n;
                        existing.commands = c;
                    }
                    save();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void load() {
        try {
            JSONArray arr = new JSONArray(item.data == null || item.data.isEmpty() ? "[]" : item.data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new Entry(o.getString("n"), o.getString("c")));
            }
        } catch (JSONException e) {
            entries.clear();
        }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : entries) arr.put(new JSONObject().put("n", e.name).put("c", e.commands));
        } catch (JSONException ignored) {
        }
        item.data = arr.toString();
        act.commitLayout();
    }
}
