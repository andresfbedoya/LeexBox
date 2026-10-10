package com.leexbox.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class TermuxBridge {
    interface Callback {
        void onResult(String stdout, String stderr, int exitCode);
    }

    interface Stream {
        void onChunk(String text);

        void onDone(int code, String cwd, String error);
    }

    static final int NO_CODE = Integer.MIN_VALUE;
    static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String HOME = "/data/data/com.termux/files/home";

    private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String END = "@@LEEXEND@@";
    private static final Map<Integer, Callback> PENDING = new HashMap<>();
    private static int seq;

    private TermuxBridge() {}

    static void reset() {
        PENDING.clear();
    }

    static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static void run(Context c, String script, Callback cb) {
        int id = ++seq;
        PENDING.put(id, cb);

        Intent back = new Intent(c, TermuxResultReceiver.class).putExtra("id", id);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, back,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent i = new Intent("com.termux.RUN_COMMAND")
                .setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", script})
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);
        try {
            c.startForegroundService(i);
        } catch (RuntimeException e) {
            PENDING.remove(id);
            cb.onResult("", "No pude hablar con Termux: " + e.getMessage(), -1);
        }
    }

    static boolean openSession(Context c, String cmd, String cwd) {
        String script = "cd " + quote(cwd) + " 2>/dev/null || cd \"$HOME\"\n" + cmd + "\n";
        Intent i = new Intent("com.termux.RUN_COMMAND")
                .setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", script})
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            c.startForegroundService(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void deliver(Intent in) {
        Callback cb = PENDING.remove(in.getIntExtra("id", -1));
        if (cb == null) return;
        Bundle r = in.getBundleExtra("result");
        if (r == null) {
            cb.onResult("", "Termux no devolvió respuesta", -1);
            return;
        }
        String err = r.getString("stderr", "");
        String msg = r.getString("errmsg");
        if (msg != null && !msg.isEmpty()) err = err + msg;
        cb.onResult(r.getString("stdout", ""), err, r.getInt("exitCode", 0));
    }

    static Runnable stream(Context c, String cmd, String cwd, Stream cb) {
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean connected = new AtomicBoolean();
        AtomicReference<ServerSocket> serverRef = new AtomicReference<>();
        AtomicReference<Socket> socketRef = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();

        new Thread(() -> {
            ServerSocket server = null;
            try {
                server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                server.setSoTimeout(15000);
                serverRef.set(server);
                String script = script(server.getLocalPort(), cmd, cwd);
                main.post(() -> run(c, script, (so, se, code) -> {
                    if (!connected.get()) {
                        failure.set(se.isEmpty() ? "Termux no pudo ejecutar el comando" : se);
                        closeQuietly(serverRef.get());
                    }
                }));
                Socket socket;
                try {
                    socket = server.accept();
                } catch (IOException e) {
                    String msg = failure.get() != null ? failure.get()
                            : "Termux no se conectó. Revisa allow-external-apps o prueba `live off`.";
                    main.post(() -> cb.onDone(NO_CODE, null, msg));
                    return;
                }
                connected.set(true);
                socketRef.set(socket);
                pump(socket, main, cb);
            } catch (IOException e) {
                String msg = "Error de socket: " + e.getMessage();
                main.post(() -> cb.onDone(NO_CODE, null, msg));
            } finally {
                closeQuietly(server);
            }
        }).start();

        return () -> {
            closeQuietly(socketRef.get());
            closeQuietly(serverRef.get());
        };
    }

    private static String script(int port, String cmd, String cwd) {
        return "exec 3<>/dev/tcp/127.0.0.1/" + port + "\n"
                + "exec >&3 2>&3\n"
                + "cd " + quote(cwd) + " 2>/dev/null || cd \"$HOME\"\n"
                + cmd + "\n"
                + "__e=$?\n"
                + "printf '\\n" + END + "%s@@%s' \"$__e\" \"$PWD\"\n"
                + "exit $__e";
    }

    private static void pump(Socket socket, Handler main, Stream cb) {
        String marker = "\n" + END;
        StringBuilder pending = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        boolean ended = false;
        try (InputStreamReader r = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) {
                if (ended) {
                    tail.append(buf, 0, n);
                    continue;
                }
                pending.append(buf, 0, n);
                int idx = pending.indexOf(marker);
                if (idx >= 0) {
                    emit(main, cb, pending.substring(0, idx));
                    tail.append(pending.substring(idx + marker.length()));
                    pending.setLength(0);
                    ended = true;
                } else {
                    int upto = pending.length() - holdLen(pending, marker);
                    if (upto > 0) {
                        emit(main, cb, pending.substring(0, upto));
                        pending.delete(0, upto);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (!ended && pending.length() > 0) emit(main, cb, pending.toString());

        int code = NO_CODE;
        String cwd = null;
        if (ended) {
            String t = tail.toString();
            int sep = t.indexOf("@@");
            if (sep > 0) {
                try {
                    code = Integer.parseInt(t.substring(0, sep).trim());
                } catch (NumberFormatException ignored) {
                }
                cwd = t.substring(sep + 2).trim();
            }
        }
        int fcode = code;
        String fcwd = cwd;
        main.post(() -> cb.onDone(fcode, fcwd, null));
    }

    private static void emit(Handler main, Stream cb, String text) {
        if (!text.isEmpty()) main.post(() -> cb.onChunk(text));
    }

    private static int holdLen(CharSequence s, String marker) {
        int max = Math.min(marker.length() - 1, s.length());
        for (int k = max; k > 0; k--) {
            if (marker.startsWith(s.subSequence(s.length() - k, s.length()).toString())) return k;
        }
        return 0;
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}
