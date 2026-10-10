package com.leexbox.launcher;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TerminalModule {
    private static final String MARK = "@@LEEXCWD@@";
    private static final int MAX_CHARS = 40000;
    private static final Set<String> INTERACTIVE = new HashSet<>(Arrays.asList(
            "nano", "vim", "vi", "nvim", "htop", "top", "btop", "cmatrix", "less", "man", "ssh", "mc", "tmux",
            "screen", "ranger"));
    private static final int ERROR = 0xFFFF8A80;
    private static final int[] ANSI = {
            0xFF6B6B6B, 0xFFFF6B6B, 0xFF7FD962, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFDCDCDC,
            0xFF9A9A9A, 0xFFFF8A80, 0xFFA5E887, 0xFFF2D58F, 0xFF8CC8FF, 0xFFDDA0F0, 0xFF7FD8E3, 0xFFFFFFFF
    };

    private final HomeActivity act;
    private final Prefs prefs;
    private final AppIndex apps;
    private final LinearLayout root;
    private final ScrollView scroll;
    private final TextView out;
    private final TextView prompt;
    private final EditText input;
    private final SpannableStringBuilder log = new SpannableStringBuilder();
    private final StringBuilder run = new StringBuilder();
    private final StringBuilder esc = new StringBuilder();
    private final Runnable flush;
    private Palette p;
    private String cwd;
    private boolean busy;
    private boolean inEsc;
    private boolean flushPending;
    private int color;
    private int lineStart;
    private int col;
    private Runnable canceller;

    TerminalModule(HomeActivity act, Prefs prefs, AppIndex apps) {
        this.act = act;
        this.prefs = prefs;
        this.apps = apps;
        this.cwd = prefs.cwd();

        int pad = Ui.dp(act, 16);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, Ui.dp(act, 8), pad, 0);

        out = mono(new TextView(act));
        out.setGravity(Gravity.BOTTOM);
        scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(out, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        out.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> scroll.scrollTo(0, b));
        View.OnClickListener focus = v -> {
            if (prefs.autoKeyboard()) focusInput();
        };
        View.OnLongClickListener settings = v -> {
            act.showSettings();
            return true;
        };
        out.setOnClickListener(focus);
        out.setOnLongClickListener(settings);
        scroll.setOnClickListener(focus);
        scroll.setOnLongClickListener(settings);

        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        prompt = mono(new TextView(act));
        prompt.setOnClickListener(v -> cancel());
        input = mono(new EditText(act));
        input.setBackground(null);
        input.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setOnEditorActionListener((v, id, e) -> {
            submit();
            return true;
        });
        row.addView(prompt, new LinearLayout.LayoutParams(-2, -2));
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        flush = () -> {
            flushPending = false;
            out.setText(log);
        };
    }

    View view() {
        return root;
    }

    boolean hit(float x, float y) {
        return Ui.hit(scroll, x, y);
    }

    boolean scrollable() {
        return scroll.canScrollVertically(-1) || scroll.canScrollVertically(1);
    }

    void clear() {
        log.clear();
        run.setLength(0);
        lineStart = 0;
        col = 0;
        out.setText("");
    }

    void apply(Palette palette) {
        p = palette;
        out.setTextColor(p.text);
        prompt.setTextColor(p.primary);
        input.setTextColor(p.text);
        updatePrompt();
    }

    void welcome() {
        if (!prefs.welcome()) return;
        say("LeexBox 1.3 listo. Escribe el nombre de una app para abrirla, o `help`.", false);
        say("Llena el dock con: dock add <app>  (máx. 5)", false);
    }

    void clearFocus() {
        input.clearFocus();
    }

    void say(String text, boolean error) {
        print(text, error ? ERROR : p.primary);
    }

    void execute(String line) {
        if (busy) {
            say("Termux sigue ocupado con el comando anterior", true);
            return;
        }
        print(promptText() + line, p.text);
        dispatch(line);
    }

    private <T extends TextView> T mono(T t) {
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(13);
        return t;
    }

    private void focusInput() {
        input.requestFocus();
        act.getSystemService(InputMethodManager.class).showSoftInput(input, 0);
    }

    private void submit() {
        String line = input.getText().toString().trim();
        input.setText("");
        if (line.isEmpty()) return;
        execute(line);
    }

    private void cancel() {
        if (!busy || canceller == null) return;
        canceller.run();
        say("^C", true);
    }

    private void dispatch(String line) {
        if (line.startsWith("!")) {
            termux(line.substring(1).trim());
            return;
        }
        String[] w = line.split("\\s+", 2);
        String arg = w.length > 1 ? w[1].trim() : "";
        switch (w[0]) {
            case "help": help(); return;
            case "clear": clear(); return;
            case "drawer": act.showDrawer(); return;
            case "restart": act.restartLauncher(); return;
            case "apps": listApps(); return;
            case "dock": dock(arg); return;
            case "open": open(arg); return;
            case "hide": hide(arg, true); return;
            case "unhide": hide(arg, false); return;
            case "hidden": listHidden(); return;
            case "t":
                if (!arg.isEmpty()) {
                    openTermux(arg);
                    return;
                }
                break;
            default: break;
        }
        if (INTERACTIVE.contains(w[0])) {
            openTermux(line);
            return;
        }
        AppIndex.App app = apps.find(line);
        if (app != null) {
            apps.launch(act, app);
        } else {
            termux(line);
        }
    }

    private void help() {
        say("<app>            abre la app (nombre o prefijo único)\n"
                + "open <app>       igual, explícito\n"
                + "apps             lista las apps visibles\n"
                + "drawer           abre el cajón de apps\n"
                + "hide <app>       oculta la app (se abre con su nombre exacto)\n"
                + "unhide <app>     la vuelve a mostrar\n"
                + "hidden           lista las apps ocultas\n"
                + "dock [add|rm|clear] <app>   (máx. 5)\n"
                + "t <cmd>          abre el comando en Termux (nano, htop…)\n"
                + "restart          reinicia el launcher (vuelve a ~)\n"
                + "clear            limpia la pantalla\n"
                + "!<cmd>           fuerza el comando a Termux\n"
                + "toca el prompt « … » para cancelar un comando\n"
                + "mantén pulsada la terminal para abrir los ajustes\n"
                + "todo lo demás se ejecuta en Termux", false);
    }

    private void openTermux(String cmd) {
        if (act.checkSelfPermission(TermuxBridge.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{TermuxBridge.PERMISSION}, 1);
            return;
        }
        if (TermuxBridge.openSession(act, cmd, cwd)) {
            say("Abriendo en Termux…", false);
        } else {
            say("No pude abrir Termux", true);
        }
    }

    private void listApps() {
        List<String> names = new ArrayList<>();
        for (AppIndex.App a : apps.visible()) names.add(a.label);
        say(String.join(", ", names), false);
    }

    private void listHidden() {
        Set<String> hidden = prefs.hidden();
        List<String> names = new ArrayList<>();
        for (AppIndex.App a : apps.all()) {
            if (hidden.contains(a.pkg)) names.add(a.label);
        }
        say(names.isEmpty() ? "No hay apps ocultas" : String.join(", ", names), false);
    }

    private void hide(String name, boolean hide) {
        AppIndex.App app = apps.find(name, !hide);
        if (app == null) {
            say("No encontré \"" + name + "\"", true);
            return;
        }
        Set<String> hidden = prefs.hidden();
        if (hide) {
            hidden.add(app.pkg);
        } else {
            hidden.remove(app.pkg);
        }
        prefs.hidden(hidden);
        say(app.label + (hide ? " oculta (se abre escribiendo su nombre exacto)" : " visible otra vez"), false);
    }

    private void open(String name) {
        AppIndex.App app = apps.find(name);
        if (app == null) {
            say("No encontré \"" + name + "\"", true);
        } else {
            apps.launch(act, app);
        }
    }

    private void dock(String arg) {
        String[] w = arg.split("\\s+", 2);
        String sub = w[0];
        String name = w.length > 1 ? w[1].trim() : "";
        List<String> pkgs = prefs.dock();

        if (sub.equals("add") || sub.equals("rm")) {
            AppIndex.App app = apps.find(name, true);
            if (app == null) {
                say("No encontré \"" + name + "\"", true);
                return;
            }
            if (sub.equals("add")) {
                if (!pkgs.contains(app.pkg) && pkgs.size() >= DockModule.MAX) {
                    say("El dock está lleno (máximo " + DockModule.MAX + "). Quita una con: dock rm <app>", true);
                    return;
                }
                if (!pkgs.contains(app.pkg)) pkgs.add(app.pkg);
            } else {
                pkgs.remove(app.pkg);
            }
            prefs.dock(pkgs);
            act.refreshDock();
            say(app.label + (sub.equals("add") ? " añadida al dock" : " quitada del dock"), false);
        } else if (sub.equals("clear")) {
            prefs.dock(new ArrayList<String>());
            act.refreshDock();
            say("Dock vacío", false);
        } else {
            List<String> names = new ArrayList<>();
            for (String pkg : pkgs) {
                AppIndex.App a = apps.byPackage(pkg);
                if (a != null) names.add(a.label);
            }
            say(names.isEmpty() ? "Dock vacío. Usa: dock add <app>" : "Dock: " + String.join(", ", names), false);
        }
    }

    private void termux(String cmd) {
        if (cmd.isEmpty()) return;
        if (act.checkSelfPermission(TermuxBridge.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{TermuxBridge.PERMISSION}, 1);
            return;
        }
        setBusy(true);
        if (prefs.live()) {
            canceller = TermuxBridge.stream(act, cmd, cwd, new TermuxBridge.Stream() {
                @Override
                public void onChunk(String text) {
                    feed(text);
                }

                @Override
                public void onDone(int code, String newCwd, String error) {
                    canceller = null;
                    setBusy(false);
                    if (newCwd != null && !newCwd.isEmpty()) {
                        cwd = newCwd;
                        prefs.cwd(cwd);
                    }
                    endLine();
                    if (error != null) {
                        print(error, ERROR);
                    } else if (code != 0 && code != TermuxBridge.NO_CODE) {
                        print("[exit " + code + "]", ERROR);
                    }
                    updatePrompt();
                }
            });
            return;
        }
        String script = "cd " + TermuxBridge.quote(cwd) + " 2>/dev/null || cd \"$HOME\"\n"
                + cmd + "\n"
                + "__e=$?\n"
                + "printf '\\n" + MARK + "%s' \"$PWD\"\n"
                + "exit $__e";
        TermuxBridge.run(act, script, (so, se, code) -> {
            setBusy(false);
            int i = so.lastIndexOf(MARK);
            if (i >= 0) {
                cwd = so.substring(i + MARK.length()).trim();
                prefs.cwd(cwd);
                so = so.substring(0, i);
            }
            so = rtrim(so);
            se = rtrim(se);
            if (!so.isEmpty()) print(so, p.text);
            if (!se.isEmpty()) print(se, ERROR);
            if (code != 0) print("[exit " + code + "]", ERROR);
            updatePrompt();
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        updatePrompt();
    }

    private void updatePrompt() {
        prompt.setText(busy ? "… " : promptText());
    }

    private String promptText() {
        String dir = cwd.startsWith(TermuxBridge.HOME) ? "~" + cwd.substring(TermuxBridge.HOME.length()) : cwd;
        return dir + " ❯ ";
    }

    private void print(String text, int c) {
        endLine();
        int saved = color;
        color = c;
        feed(text + "\n");
        color = saved;
    }

    private void endLine() {
        flushRun();
        if (log.length() > lineStart) newline();
    }

    private void feed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inEsc) {
                esc.append(c);
                if (esc.length() == 1) {
                    if (c != '[' && c != ']') inEsc = false;
                } else if (esc.charAt(0) == '[') {
                    if (c >= '@' && c <= '~') {
                        inEsc = false;
                        csi(esc.toString());
                    }
                } else if (c == 7 || c == '\\') {
                    inEsc = false;
                }
                continue;
            }
            switch (c) {
                case '\u001b':
                    flushRun();
                    inEsc = true;
                    esc.setLength(0);
                    break;
                case '\r':
                    flushRun();
                    col = 0;
                    break;
                case '\n':
                    flushRun();
                    newline();
                    break;
                case '\b':
                    flushRun();
                    if (col > 0) col--;
                    break;
                case '\t':
                    int spaces = 8 - ((col + run.length()) % 8);
                    for (int k = 0; k < spaces; k++) run.append(' ');
                    break;
                default:
                    if (c >= ' ') run.append(c);
                    break;
            }
        }
        flushRun();
        if (!flushPending) {
            flushPending = true;
            out.postDelayed(flush, 40);
        }
    }

    private void flushRun() {
        if (run.length() == 0) return;
        String text = run.toString();
        run.setLength(0);
        int pos = lineStart + col;
        while (log.length() < pos) log.append(' ');
        int end = Math.min(log.length(), pos + text.length());
        log.replace(pos, end, text);
        log.setSpan(new ForegroundColorSpan(color == 0 ? p.text : color), pos, pos + text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        col += text.length();
        trim();
    }

    private void newline() {
        log.append('\n');
        lineStart = log.length();
        col = 0;
        trim();
    }

    private void trim() {
        if (log.length() <= MAX_CHARS) return;
        int cut = log.length() - MAX_CHARS;
        log.delete(0, cut);
        lineStart = Math.max(0, lineStart - cut);
    }

    private void csi(String seq) {
        char cmd = seq.charAt(seq.length() - 1);
        String params = seq.substring(1, seq.length() - 1);
        switch (cmd) {
            case 'm':
                sgr(params);
                break;
            case 'K':
                if (params.equals("2")) {
                    log.delete(lineStart, log.length());
                    col = 0;
                } else {
                    int from = Math.min(log.length(), lineStart + col);
                    log.delete(from, log.length());
                }
                break;
            case 'J':
                if (params.equals("2") || params.equals("3")) {
                    log.clear();
                    lineStart = 0;
                    col = 0;
                }
                break;
            case 'G':
                col = Math.max(0, num(params, 1) - 1);
                break;
            case 'D':
                col = Math.max(0, col - num(params, 1));
                break;
            default:
                break;
        }
    }

    private void sgr(String params) {
        String[] parts = params.isEmpty() ? new String[]{"0"} : params.split(";", -1);
        for (int i = 0; i < parts.length; i++) {
            int n = num(parts[i], 0);
            if (n == 0 || n == 39) {
                color = 0;
            } else if (n >= 30 && n <= 37) {
                color = ANSI[n - 30];
            } else if (n >= 90 && n <= 97) {
                color = ANSI[n - 90 + 8];
            } else if (n == 38 && i + 1 < parts.length) {
                int mode = num(parts[i + 1], 0);
                if (mode == 5 && i + 2 < parts.length) {
                    color = xterm(num(parts[i + 2], 0));
                    i += 2;
                } else if (mode == 2 && i + 4 < parts.length) {
                    color = Color.rgb(num(parts[i + 2], 0), num(parts[i + 3], 0), num(parts[i + 4], 0));
                    i += 4;
                }
            }
        }
    }

    private static int xterm(int n) {
        if (n < 16) return ANSI[Math.max(0, n)];
        if (n < 232) {
            int v = n - 16;
            return Color.rgb(level(v / 36), level((v / 6) % 6), level(v % 6));
        }
        int g = Math.min(255, 8 + 10 * (n - 232));
        return Color.rgb(g, g, g);
    }

    private static int level(int x) {
        return x == 0 ? 0 : 55 + 40 * x;
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s.replace("?", "").trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
