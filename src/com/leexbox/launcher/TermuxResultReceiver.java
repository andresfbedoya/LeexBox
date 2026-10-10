package com.leexbox.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TermuxResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TermuxBridge.deliver(intent);
    }
}
