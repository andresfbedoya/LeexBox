package com.leexbox.launcher;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

final class StatsModule {
    private final HomeActivity act;
    private final TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTotal;
    private long lastIdle;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            if (running) handler.postDelayed(this, 2000);
        }
    };

    StatsModule(HomeActivity act) {
        this.act = act;
        view = new TextView(act);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(act, 16), Ui.dp(act, 6), Ui.dp(act, 16), Ui.dp(act, 6));
    }

    View view() {
        return view;
    }

    void apply(Palette p) {
        view.setTextColor(p.text);
        view.setBackground(Ui.round(Ui.alpha(p.base, 0xCC), Ui.dp(act, 20)));
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void update() {
        view.setText("CPU " + cpu() + "   RAM " + ram() + "   " + temp());
    }

    private String cpu() {
        long[] s = readStat();
        if (s == null) return freq();
        String r = lastTotal == 0 ? "…" : (100 * ((s[0] - lastTotal) - (s[1] - lastIdle)) / Math.max(1, s[0] - lastTotal)) + "%";
        lastTotal = s[0];
        lastIdle = s[1];
        return r;
    }

    private long[] readStat() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            String[] t = r.readLine().trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < t.length; i++) total += Long.parseLong(t[i]);
            return new long[]{total, Long.parseLong(t[4]) + Long.parseLong(t[5])};
        } catch (Exception e) {
            return null;
        }
    }

    private String freq() {
        try {
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 16; i++) {
                File dir = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq");
                if (!dir.exists()) continue;
                long max = readLong(new File(dir, "cpuinfo_max_freq"));
                if (max <= 0) continue;
                sum += (double) readLong(new File(dir, "scaling_cur_freq")) / max;
                n++;
            }
            return n == 0 ? "—" : "~" + Math.round(100 * sum / n) + "%";
        } catch (Exception e) {
            return "—";
        }
    }

    private static long readLong(File f) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return Long.parseLong(r.readLine().trim());
        }
    }

    private String ram() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        act.getSystemService(ActivityManager.class).getMemoryInfo(mi);
        return Math.round(100f * (mi.totalMem - mi.availMem) / mi.totalMem) + "%";
    }

    private String temp() {
        Intent b = act.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int t = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        return String.format(Locale.ROOT, "%.0f°C", t / 10f);
    }
}
