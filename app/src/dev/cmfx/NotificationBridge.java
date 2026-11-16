package dev.cmfx;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** Converts Android notifications into the watch's compact companion format. */
public final class NotificationBridge extends NotificationListenerService {
    @Override public void onListenerConnected() {
        Intent i = new Intent(this, WatchService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        if (e == null) return;
        String title = String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE, ""));
        CharSequence textValue = e.getCharSequence(Notification.EXTRA_TEXT);
        String text = textValue == null ? "" : textValue.toString();
        if (title.isEmpty() && text.isEmpty()) return;
        String pkg = sbn.getPackageName();
        int icon;
        String cat = e.getString("android.app.Notification.category");
        if (pkg != null && pkg.toLowerCase().contains("whatsapp")) icon = 8;
        else if (cat != null && cat.toLowerCase().contains("call")) icon = 15;
        else icon = 0;
        WatchService.forward(icon, title, text);
        android.util.Log.i("CMFx", "Notification [" + pkg + "] icon=" + icon
                + " \"" + title + "\": " + text);
    }
}
