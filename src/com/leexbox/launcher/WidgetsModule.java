package com.leexbox.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;

final class WidgetsModule {
    private static final int HOST_ID = 4242;

    private final AppWidgetHost host;
    private final AppWidgetManager manager;

    WidgetsModule(HomeActivity act) {
        host = new AppWidgetHost(act, HOST_ID);
        manager = AppWidgetManager.getInstance(act);
    }

    AppWidgetHost host() {
        return host;
    }

    AppWidgetManager manager() {
        return manager;
    }

    int allocate() {
        return host.allocateAppWidgetId();
    }

    void delete(int id) {
        host.deleteAppWidgetId(id);
    }

    void start() {
        host.startListening();
    }

    void stop() {
        host.stopListening();
    }
}
