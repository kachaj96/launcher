package com.adrian.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.adrian.launcher.ui.Polish
import java.text.Collator
import java.text.Normalizer

data class AppEntry(
    val label: String,
    val originalLabel: String,
    val component: ComponentName,
    val user: UserHandle,
    /** White monochrome icon, or null when the app ships no monochrome layer. */
    val icon: ImageBitmap?,
) {
    val key: String = "${component.flattenToString()}#${user.hashCode()}"
    val packageName: String get() = component.packageName

    /** Section letter for the alphabet bar: first letter, or "#" for digits and symbols. */
    val section: String = label.firstOrNull()?.uppercaseChar()
        ?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"

    /** Lowercase, accent-free form used for search ("zabka" finds "Żabka"); covers the original name too. */
    val searchKey: String = (label + "\u0000" + originalLabel).foldForSearch()
}

fun String.foldForSearch(): String =
    Normalizer.normalize(lowercase(Polish), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

val appCollator: Collator = Collator.getInstance(Polish).apply { strength = Collator.SECONDARY }

fun List<AppEntry>.sortedByLabel(): List<AppEntry> = sortedWith { a, b -> appCollator.compare(a.label, b.label) }

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconSizePx = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)

    /** All launchable activities across profiles (incl. work profile / Secure Folder), sorted the Polish way. */
    fun loadApps(): List<AppEntry> {
        val self = context.packageName
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.applicationInfo.packageName != self }
            .map { info ->
                val label = info.label.toString().trim()
                AppEntry(label, label, info.componentName, info.user, monochromeIcon(info))
            }
            .sortedByLabel()
    }

    /**
     * Renders the adaptive icon's monochrome layer in white. The layer is drawn on a 108dp
     * canvas whose inner 72dp is visible, so it is drawn 1.5x larger and centred.
     */
    private fun monochromeIcon(info: LauncherActivityInfo): ImageBitmap? = runCatching {
        val adaptive = info.getIcon(0) as? AdaptiveIconDrawable ?: return null
        val mono = adaptive.monochrome?.mutate() ?: return null
        val size = iconSizePx
        val inset = size / 4
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        mono.setBounds(-inset, -inset, size + inset, size + inset)
        mono.setTint(android.graphics.Color.WHITE)
        mono.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()

    fun launch(app: AppEntry, sourceBounds: Rect? = null) {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, null)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
