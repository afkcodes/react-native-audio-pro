package dev.rnap.reactnativeaudiopro

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.common.MediaItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures

open class AudioProPlaybackService : MediaLibraryService() {

	private lateinit var mediaLibrarySession: MediaLibrarySession
	private lateinit var ambientLibrarySession: MediaLibrarySession
	private lateinit var player: ExoPlayer
	private lateinit var ambientPlayer: ExoPlayer
	private val equalizer = AudioProEqualizer()

	companion object {
		private const val NOTIFICATION_ID = Constants.NOTIFICATION_ID
		private const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID
	}

	/**
	 * Brings app to foreground when notification or session is tapped.
	 */
	fun getSessionActivityIntent(): PendingIntent? {
		val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
			action = android.content.Intent.ACTION_MAIN
			addCategory(android.content.Intent.CATEGORY_LAUNCHER)
			flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
				android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		return launchIntent?.let {
			PendingIntent.getActivity(
				this,
				Constants.PENDING_INTENT_REQUEST_CODE,
				it,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
		}
	}

	/**
	 * Creates the library session callback to implement the domain logic. Can be overridden to return
	 * an alternative callback, for example a subclass of [AudioProMediaLibrarySessionCallback].
	 *
	 * This method is called when the session is built by the [AudioProPlaybackService].
	 */
	@OptIn(UnstableApi::class)
	protected open fun createLibrarySessionCallback(): MediaLibrarySession.Callback {
		return AudioProMediaLibrarySessionCallback(this)
	}
	
	private fun createAmbientLibrarySessionCallback(): MediaLibrarySession.Callback {
		return object : MediaLibrarySession.Callback {
			override fun onAddMediaItems(
				mediaSession: MediaSession,
				controller: ControllerInfo,
				mediaItems: MutableList<MediaItem>
			): ListenableFuture<List<MediaItem>> {
				return com.google.common.util.concurrent.Futures.immediateFuture(mediaItems)
			}
		}
	}

	@OptIn(UnstableApi::class) // MediaSessionService.setListener
	override fun onCreate() {
		super.onCreate()
		// Use the new Media3 standard notification provider
		setMediaNotificationProvider(AudioProNotificationProvider(this))
		
		initializeSessionAndPlayer()
	}

	override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession? {
		val hints = controllerInfo.connectionHints
		if (hints.getString("type") == "ambient") {
			return ambientLibrarySession
		}
		return mediaLibrarySession
	}

	// Legacy listener removed as MediaSessionService handles foreground/background automatically
	// with the notification provider.

	/**
	 * Called when the task is removed from the recent tasks list
	 * This happens when the user swipes away the app from the recent apps list
	 */
	override fun onTaskRemoved(rootIntent: android.content.Intent?) {
		android.util.Log.d(Constants.LOG_TAG, "Task removed, stopping service")

		// Force stop playback and release resources
		try {
			// Main Session
			val hasSession = ::mediaLibrarySession.isInitialized
			if (hasSession) {
				mediaLibrarySession.player.stop()
				mediaLibrarySession.release()
			}
			if (::player.isInitialized) {
				player.release()
			}
			
			// Ambient Session
			if (::ambientLibrarySession.isInitialized) {
				ambientLibrarySession.player.stop()
				ambientLibrarySession.release()
			}
			if (::ambientPlayer.isInitialized) {
				ambientPlayer.release()
			}
		} catch (e: Exception) {
			android.util.Log.e(Constants.LOG_TAG, "Error stopping playback", e)
		}

		stopSelf()

		super.onTaskRemoved(rootIntent)
	}

	@OptIn(UnstableApi::class)
	override fun onDestroy() {
		android.util.Log.d(Constants.LOG_TAG, "Service being destroyed")

		// Make sure to release all resources
		try {
			// Main Session
			val hasSession = ::mediaLibrarySession.isInitialized
			if (hasSession) {
				mediaLibrarySession.player.stop()
				mediaLibrarySession.release()
			}
			if (::player.isInitialized) {
				player.release()
			}
			
			// Ambient Session
			if (::ambientLibrarySession.isInitialized) {
				ambientLibrarySession.player.stop()
				ambientLibrarySession.release()
			}
			if (::ambientPlayer.isInitialized) {
				ambientPlayer.release()
			}
		} catch (e: Exception) {
			android.util.Log.e(Constants.LOG_TAG, "Error during service destruction", e)
		}

		super.onDestroy()
	}

	@OptIn(UnstableApi::class)
	private fun initializeSessionAndPlayer() {
		// Create a composite data source factory that can handle both HTTP and file URIs
		val dataSourceFactory = object : DataSource.Factory {
			override fun createDataSource(): DataSource {
				// Create HTTP data source factory with custom headers if available
				val httpDataSourceFactory = DefaultHttpDataSource.Factory()
					.setAllowCrossProtocolRedirects(true)
					.setUserAgent("AudioPro/1.0")

				// Apply custom headers if they exist
				AudioProController.headersAudio?.let { headers ->
					if (headers.isNotEmpty()) {
						httpDataSourceFactory.setDefaultRequestProperties(headers)
						android.util.Log.d(
							Constants.LOG_TAG,
							"Applied custom headers: $headers"
						)
					}
				}

				// Create a DefaultDataSource that will handle both HTTP and file URIs
				// It will delegate to FileDataSource for file:// URIs and to HttpDataSource for http(s):// URIs
				val upstreamFactory = DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)

				// Wrap with CacheDataSource only if enabled
				if (AudioProController.settingCacheEnabled) {
					return AudioProCache.createDataSourceFactory(applicationContext, upstreamFactory)
						.createDataSource()
				} else {
					return upstreamFactory.createDataSource()
				}
			}
		}

		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

		player =
			ExoPlayer.Builder(this)
				.setMediaSourceFactory(mediaSourceFactory)
				.setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(AudioProController.settingAudioContentType)
						.build(),
					/* handleAudioFocus = */ true
				)
				.build()
		player.setHandleAudioBecomingNoisy(true)
		player.repeatMode = Player.REPEAT_MODE_OFF
		player.addAnalyticsListener(EventLogger())
		
		// Initialize Equalizer with current or future session ID
		player.addListener(object : Player.Listener {
			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				super.onAudioSessionIdChanged(audioSessionId)
				android.util.Log.i(Constants.LOG_TAG, "Audio Session ID changed: $audioSessionId")
				if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
					equalizer.initialize(audioSessionId)
				}
			}
		})

		mediaLibrarySession =
			MediaLibrarySession.Builder(this, player, createLibrarySessionCallback())
				.also { builder -> getSessionActivityIntent()?.let { builder.setSessionActivity(it) } }
				.build()
				
		// Initialize Ambient Player
		ambientPlayer = ExoPlayer.Builder(this)
			.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(C.USAGE_MEDIA)
					.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
					.build(),
				true
			)
			.build()
		ambientPlayer.repeatMode = Player.REPEAT_MODE_ONE // Default loop for ambient? Controller sets it anyway.
		
		ambientLibrarySession = MediaLibrarySession.Builder(this, ambientPlayer, createAmbientLibrarySessionCallback())
			.setId("AmbientSession")
			.build()
			
		addSession(mediaLibrarySession)
		addSession(ambientLibrarySession)
	}

	fun handleAmbientPlay(args: android.os.Bundle) {
		val url = args.getString("url") ?: return
		val loop = if (args.containsKey("loop")) args.getBoolean("loop") else true
		val vol = args.getFloat("volume", 1.0f)
		
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientPlay url=$url loop=$loop vol=$vol")
		
		val uri = android.net.Uri.parse(url)
		val mediaItem = MediaItem.Builder().setUri(uri).build()
		
		ambientPlayer.setMediaItem(mediaItem)
		ambientPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
		ambientPlayer.volume = vol
		ambientPlayer.prepare()
		ambientPlayer.play()
	}
	
	fun handleAmbientStop() {
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientStop")
		if (::ambientPlayer.isInitialized) {
			ambientPlayer.stop()
			ambientPlayer.clearMediaItems()
		}
	}
	
	fun handleAmbientPause() {
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientPause")
		if (::ambientPlayer.isInitialized) {
			ambientPlayer.pause()
		}
	}
	
	fun handleAmbientResume() {
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientResume")
		if (::ambientPlayer.isInitialized) {
			ambientPlayer.play()
		}
	}
	
	fun handleAmbientSeek(pos: Long) {
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientSeek to $pos")
		if (::ambientPlayer.isInitialized) {
			ambientPlayer.seekTo(pos)
		}
	}
	
	fun handleAmbientSetVolume(vol: Float) {
		android.util.Log.d(Constants.LOG_TAG, "Service: ambientSetVolume to $vol")
		if (::ambientPlayer.isInitialized) {
			ambientPlayer.volume = vol
		}
	}

	fun handleSetEqualizer(gains: FloatArray) {
		// Ensure session is initialized if we have a player
		if (::player.isInitialized && player.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
			equalizer.initialize(player.audioSessionId)
		}
		equalizer.setGains(gains)
	}
	
	fun handleSetBassBoost(strength: Int) {
		// Ensure session is initialized if we have a player
		if (::player.isInitialized && player.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
			equalizer.initialize(player.audioSessionId)
		}
		equalizer.setBassBoost(strength)
	}

	fun handleSetRepeatMode(mode: String) {
		if (::player.isInitialized) {
			val repeatMode = when (mode) {
				"ONE" -> Player.REPEAT_MODE_ONE
				"ALL" -> Player.REPEAT_MODE_ALL
				else -> Player.REPEAT_MODE_OFF
			}
			player.repeatMode = repeatMode
		}
	}

	fun handleSetShuffleMode(enabled: Boolean) {
		if (::player.isInitialized) {
			player.shuffleModeEnabled = enabled
		}
	}

	fun handleSetSkipSilence(enabled: Boolean) {
		if (::player.isInitialized) {
			player.skipSilenceEnabled = enabled
			// When enabling skip silence, we might want to ensure the parameters are set correctly if needed
			// But default should work.
			android.util.Log.d(Constants.LOG_TAG, "Set skip silence enabled: $enabled")
		}
	}

}
