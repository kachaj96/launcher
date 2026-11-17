package com.adrian.launcher.system

import android.app.ActivityOptions
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is playing right now, with the controller to pause or skip it. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val packageName: String,
    val controller: MediaController,
) {
    fun togglePlay() {
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }

    fun next() = controller.transportControls.skipToNext()

    fun previous() = controller.transportControls.skipToPrevious()

    /** Opens the player's own screen; false if the session offers none. */
    fun open(): Boolean {
        val intent = controller.sessionActivity ?: return false
        return runCatching {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            intent.send(null, 0, null, null, null, null, options.toBundle())
        }.isSuccess
    }
}

/** Shared state between the listener service and the launcher UI. */
object NotificationState {
    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Packages with at least one dismissable, non-ongoing notification (for the dots). */
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    internal fun setPackages(p: Set<String>) {
        _packages.value = p
    }

    internal fun setNowPlaying(n: NowPlaying?) {
        _nowPlaying.value = n
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun component(context: Context) = ComponentName(context, NotificationService::class.java)
}

/**
 * Notification access gives two things: which apps have unread notifications (dots) and
 * the active media sessions ("Teraz odtwarzane"). Nothing is stored or sent anywhere.
 */
class NotificationService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private val controllers = mutableListOf<Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty())
    }

    override fun onListenerConnected() {
        refreshDots()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        runCatching {
            val component = NotificationState.component(this)
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            watch(manager.getActiveSessions(component))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatchAll()
        NotificationState.setPackages(emptySet())
        NotificationState.setNowPlaying(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshDots()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshDots()

    private fun refreshDots() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationState.setPackages(
            active.filter {
                it.isClearable && !it.isOngoing &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }.map { it.packageName }.toSet(),
        )
    }

    private fun watch(list: List<MediaController>) {
        unwatchAll()
        list.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() = publish()
            }
            controller.registerCallback(callback, main)
            controllers += controller to callback
        }
        publish()
    }

    private fun unwatchAll() {
        controllers.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        controllers.clear()
    }

    /** The playing session wins; otherwise a paused one that still has a title. */
    private fun publish() {
        val candidates = controllers.map { it.first }.filter {
            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.isNotBlank() == true
        }
        val pick = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        NotificationState.setNowPlaying(
            pick?.let { c ->
                val m = c.metadata
                NowPlaying(
                    title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    packageName = c.packageName,
                    controller = c,
                )
            },
        )
    }
}
