package com.adrian.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import com.adrian.launcher.ui.Polish
import java.text.Collator
import java.text.Normalizer

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    val key: String = "${component.flattenToString()}#${user.hashCode()}"

    /** Section letter for the alphabet bar: first letter, or "#" for digits and symbols. */
    val section: String = label.firstOrNull()?.uppercaseChar()
        ?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"

    /** Lowercase, accent-free form used for search ("zabka" finds "Żabka"). */
    val searchKey: String = label.foldForSearch()
}

fun String.foldForSearch(): String =
    Normalizer.normalize(lowercase(Polish), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val collator: Collator = Collator.getInstance(Polish).apply {
        strength = Collator.SECONDARY
    }

    /** All launchable activities across profiles (incl. work profile / Secure Folder), sorted the Polish way. */
    fun loadApps(): List<AppEntry> {
        val self = context.packageName
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.applicationInfo.packageName != self }
            .map { AppEntry(it.label.toString().trim(), it.componentName, it.user) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(app: AppEntry, sourceBounds: Rect? = null) {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, null)
    }

    fun registerChanges(onChange: () -> Unit): LauncherApps.Callback {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unregisterChanges(callback: LauncherApps.Callback) {
        launcherApps.unregisterCallback(callback)
    }
}
