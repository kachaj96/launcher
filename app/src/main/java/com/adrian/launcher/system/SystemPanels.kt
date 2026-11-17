package com.adrian.launcher.system

import android.annotation.SuppressLint
import android.content.Context

object SystemPanels {

    /**
     * Pulls down the notification shade through the hidden StatusBarManager API
     * (needs EXPAND_STATUS_BAR). Returns false if the call is blocked or missing,
     * so the caller can switch the gesture off instead of failing silently.
     */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager")
            .getMethod("expandNotificationsPanel")
            .invoke(service)
        true
    }.getOrDefault(false)
}
