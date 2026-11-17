package com.adrian.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.SizeF

/** The user's own calendar app; its widgets are listed first in settings. */
const val CALENDAR_APP_PACKAGE = "com.kachaj96.kalendarz"

/** One widget a user can put on the calendar page. */
data class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
) {
    val key: String = "${info.provider.flattenToString()}#${info.profile.hashCode()}"
    val fromCalendarApp: Boolean get() = info.provider.packageName == CALENDAR_APP_PACKAGE
}

/**
 * Hosts the one widget on the calendar page. Widget ids are allocated by this host and
 * stay bound across restarts; the id is kept in [LauncherSettings.calendarWidgetId].
 */
class Widgets(private val context: Context) {

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context.applicationContext, HOST_ID)

    /** All widget providers across profiles, the calendar app first, then by app and name. */
    fun choices(): List<WidgetChoice> {
        val pm = context.packageManager
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        return launcherApps.profiles
            .flatMap { manager.getInstalledProvidersForProfile(it) }
            .map { info ->
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
                WidgetChoice(info, info.loadLabel(pm) ?: info.provider.shortClassName, appLabel)
            }
            .sortedWith(compareBy<WidgetChoice>({ !it.fromCalendarApp }, { it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun info(id: Int): AppWidgetProviderInfo? =
        if (id == NO_WIDGET) null else runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun delete(id: Int) {
        if (id != NO_WIDGET) runCatching { host.deleteAppWidgetId(id) }
    }

    /** A host view that tells the widget its real size in dp whenever its layout changes. */
    fun createView(activityContext: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        val view = host.createView(activityContext, id, info)
        view.addOnLayoutChangeListener { v, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                val d = v.resources.displayMetrics.density
                runCatching {
                    (v as AppWidgetHostView).updateAppWidgetSize(Bundle(), listOf(SizeF((r - l) / d, (b - t) / d)))
                }
            }
        }
        return view
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    companion object {
        const val HOST_ID = 1024
    }
}
