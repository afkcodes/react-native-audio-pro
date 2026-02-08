package dev.rnap.reactnativeaudiopro

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlin.math.abs

import android.app.PendingIntent
import java.util.ArrayList

object AudioProController {
	private const val DUPLICATE_POSITION_EPSILON_MS = 250L

	private var reactContext: ReactApplicationContext? = null
	private lateinit var engineBrowserFuture: ListenableFuture<MediaBrowser>
	private var enginerBrowser: MediaBrowser? = null
	private var engineBrowserConnecting: Boolean = false
	private var engineProgressHandler: Handler? = null
	private var engineProgressRunnable: Runnable? = null
	private var enginePlayerListener: Player.Listener? = null
	
	// Sleep Timer
	private var sleepTimerHandler: Handler? = null
	private var sleepTimerRunnable: Runnable? = null
	
	private val engineBrowserConnectionListener =
		object : MediaBrowser.Listener {
			override fun onDisconnected(controller: MediaController) {
				log("MediaBrowser disconnected, clearing cached instance")
				handleBrowserDisconnected(controller)
			}
		}

	private var activeTrack: ReadableMap? = null
	private var activeVolume: Float = 1.0f
	private var activePlaybackSpeed: Float = 1.0f

	private var flowIsInErrorState: Boolean = false
	private var flowLastEmittedState: String = ""
	private var flowLastEmittedPosition: Long? = null
	private var flowLastEmittedDuration: Long? = null
	private var flowLastStateEmittedTimeMs: Long = 0L
	private var flowPendingSeekPosition: Long? = null

	private var settingDebug: Boolean = false
	private var settingDebugIncludesProgress: Boolean = false
	private var settingProgressIntervalMs: Long = 1000
	var settingAudioContentType: Int = C.AUDIO_CONTENT_TYPE_MUSIC
	var settingNotificationButtons: List<String> = listOf("PREV", "NEXT")
	var settingSkipIntervalMs: Long = 30000L
	var settingCacheEnabled: Boolean = true

	var headersAudio: Map<String, String>? = null
	var headersArtwork: Map<String, String>? = null

	fun log(vararg args: Any?) {
		if (settingDebug) {
			if (!settingDebugIncludesProgress && args.isNotEmpty() && args[0] == AudioProModule.EVENT_TYPE_PROGRESS) {
				return
			}
			val msg = args.joinToString(" ")
			Log.d(Constants.LOG_TAG, msg)
			
			// Always emit to JS for debugging if context is available
			try {
				if (reactContext != null && reactContext!!.hasActiveCatalystInstance()) {
					val params = Arguments.createMap()
					params.putString("message", msg)
					reactContext!!
						.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
						.emit("AudioProLog", params)
				}
			} catch (e: Exception) {
				// Ignore
			}
		}
	}

	fun setReactContext(context: ReactApplicationContext?) {
		reactContext = context
	}

	private suspend fun ensureSession() {
		if (enginerBrowser == null) {
			if (engineBrowserConnecting) {
				// Wait for pending connection
				while (engineBrowserConnecting || enginerBrowser == null) {
					kotlinx.coroutines.delay(50)
					if (!engineBrowserConnecting && enginerBrowser == null) {
						// Connection attempted but failed or finished without result?
						// Try to init again if still null
						break 
					}
				}
			}
			
			if (enginerBrowser == null) {
				internalPrepareSession()
			}
		}
	}

	private fun hasConnectedBrowser(): Boolean {
		return enginerBrowser?.isConnected == true
	}

	private fun handleBrowserDisconnected(controller: MediaController) {
		runOnUiThread {
			// Only clear if we're dealing with the active controller reference
			if (enginerBrowser == controller) {
				detachPlayerListener()
				stopProgressTimer()
				if (::engineBrowserFuture.isInitialized) {
					MediaBrowser.releaseFuture(engineBrowserFuture)
				}
				enginerBrowser = null
				engineBrowserConnecting = false
			} else {
				log(
					"Ignoring disconnect from stale MediaBrowser instance. Active=$enginerBrowser, disconnected=$controller"
				)
			}
		}
	}

	private suspend fun internalPrepareSession() {
		if (engineBrowserConnecting) {
			// Double check locking
			while (engineBrowserConnecting) {
				kotlinx.coroutines.delay(50)
			}
			return
		}
		
		val context = reactContext ?: run {
			log("React context unavailable, skipping MediaBrowser initialization")
			return
		}
		
		engineBrowserConnecting = true
		try {
			log("Preparing MediaBrowser session")
			val token =
				SessionToken(
					context,
					ComponentName(context, AudioProPlaybackService::class.java)
				)
			
			// We need to wait for the future to complete
			// Guava ListenableFuture to Coroutine
			engineBrowserFuture =
				MediaBrowser.Builder(context, token)
					.setListener(engineBrowserConnectionListener)
					.buildAsync()
					
			enginerBrowser = engineBrowserFuture.await()
			attachPlayerListener()
			log("MediaBrowser is ready")
		} catch (e: Exception) {
			log("Failed to connect MediaBrowser: ${e.message}")
		} finally {
			engineBrowserConnecting = false
		}
	}


	// Data class to hold parsed play options
	private data class PlaybackOptions(
		val contentType: String,
		val enableDebug: Boolean,
		val includeProgressInDebug: Boolean,
		val speed: Float,
		val volume: Float,
		val autoPlay: Boolean,
		val startTimeMs: Long?,
		val progressIntervalMs: Long,
		val skipIntervalMs: Long,
		val addTrack: Boolean = false, // If true, adds track to queue instead of replacing
	)

	// Extracts and applies play options from JS before playback
	// Enforces mutual exclusivity between next/prev and skip controls for session config.
	private fun extractPlaybackOptions(options: ReadableMap): PlaybackOptions {
		val addTrack = if (options.hasKey("addTrack")) options.getBoolean("addTrack") else false
		val contentType = if (options.hasKey("contentType")) {
			options.getString("contentType") ?: "MUSIC"
		} else "MUSIC"
		val enableDebug = options.hasKey("debug") && options.getBoolean("debug")
		val includeProgressInDebug =
			options.hasKey("debugIncludesProgress") && options.getBoolean("debugIncludesProgress")
		val speed = if (options.hasKey("playbackSpeed")) options.getDouble("playbackSpeed")
			.toFloat() else 1.0f
		val volume = if (options.hasKey("volume")) options.getDouble("volume").toFloat() else 1.0f
		val autoPlay = if (options.hasKey("autoPlay")) options.getBoolean("autoPlay") else true
		val startTimeMs =
			if (options.hasKey("startTimeMs")) options.getDouble("startTimeMs").toLong() else null
			if (options.hasKey("startTimeMs")) options.getDouble("startTimeMs").toLong() else null
		val progressInterval =
			if (options.hasKey("progressIntervalMs")) options.getDouble("progressIntervalMs")
				.toLong() else 1000L
		val skipIntervalMs =
			if (options.hasKey("skipIntervalMs")) options.getDouble("skipIntervalMs").toLong() else 30000L
		val cacheEnabled = if (options.hasKey("cacheEnabled")) options.getBoolean("cacheEnabled") else true

		// Apply to controller state
		settingDebug = enableDebug
		settingDebugIncludesProgress = includeProgressInDebug
		settingAudioContentType = when (contentType) {
			"SPEECH" -> C.AUDIO_CONTENT_TYPE_SPEECH
			else -> C.AUDIO_CONTENT_TYPE_MUSIC
		}
		activePlaybackSpeed = speed
		activeVolume = volume
		settingProgressIntervalMs = progressInterval
		settingSkipIntervalMs = skipIntervalMs
		if (settingCacheEnabled != cacheEnabled) {
			log("Cache enabled setting changed to: $cacheEnabled. Requires session restart to take effect.")
		}
		settingCacheEnabled = cacheEnabled

		return PlaybackOptions(
			contentType,
			enableDebug,
			includeProgressInDebug,
			speed,
			volume,
			autoPlay,
			startTimeMs,
			progressInterval,
			skipIntervalMs,
			addTrack,
		)
	}

	/**
	 * Prepares the player for new playback without emitting state changes or destroying the media session
	 * - This function:
	 * - Pauses the player if it's playing
	 * - Stops the progress timer
	 * - Does not emit any state or clear currentTrack
	 * - Does not destroy the media session
	 */
	private fun prepareForNewPlayback() {
		log("Preparing for new playback")

		runOnUiThread {
			enginerBrowser?.pause()
		}

		stopProgressTimer()

		flowPendingSeekPosition = null
		flowIsInErrorState = false
		flowLastEmittedState = ""
		flowLastEmittedPosition = null
		flowLastEmittedDuration = null
	}

	private fun toMediaItem(track: ReadableMap): MediaItem {
		val url = track.getString("url") ?: ""
		val title = track.getString("title") ?: "Unknown Title"
		val artist = track.getString("artist") ?: "Unknown Artist"
		val album = track.getString("album") ?: "Unknown Album"
		val artwork = track.getString("artwork")?.toUri()

		val metadataBuilder = MediaMetadata.Builder()
			.setTitle(title)
			.setArtist(artist)
			.setAlbumTitle(album)

		if (artwork != null) {
			metadataBuilder.setArtworkUri(artwork)
		}

		// Serialize the full track object into extras for retrieval
		val extras = Arguments.toBundle(track)
		metadataBuilder.setExtras(extras)

		val uri = url.toUri()
		val builder = MediaItem.Builder()
			.setUri(uri)
			.setMediaId(track.getString("id") ?: "track_${System.currentTimeMillis()}")
			.setMediaMetadata(metadataBuilder.build())

		if (url.contains(".m3u8") || track.getString("type") == "hls") {
			builder.setMimeType(MimeTypes.APPLICATION_M3U8)
			log("Detected HLS content for url: $url")
		}

		return builder.build()
	}


	
	suspend fun addToQueue(tracks: com.facebook.react.bridge.ReadableArray) {
		ensureSession()
		val items = ArrayList<MediaItem>()
		for (i in 0 until tracks.size()) {
			tracks.getMap(i)?.let {
				items.add(toMediaItem(it))
			}
		}
		runOnUiThread {
			enginerBrowser?.addMediaItems(items)
			log("Added ${items.size} tracks to queue")
		}
	}
	
	suspend fun addToQueue(track: ReadableMap) {
		ensureSession()
		val item = toMediaItem(track)
		runOnUiThread {
			enginerBrowser?.addMediaItem(item)
			log("Added track to queue: ${track.getString("title")}")
		}
	}

	suspend fun clearQueue() {
		ensureSession()
		runOnUiThread {
			enginerBrowser?.stop()
			enginerBrowser?.clearMediaItems()
			log("Stopped playback and cleared queue")
		}
	}


	suspend fun skipTo(index: Int) {
		ensureSession()
		runOnUiThread {
			if (index >= 0 && index < (enginerBrowser?.mediaItemCount ?: 0)) {
				enginerBrowser?.seekToDefaultPosition(index)
				enginerBrowser?.play()
				log("Skipped to index $index")
			}
		}
	}

	suspend fun removeTrack(index: Int) {
		ensureSession()
		runOnUiThread {
			if (index >= 0 && index < (enginerBrowser?.mediaItemCount ?: 0)) {
				enginerBrowser?.removeMediaItem(index)
				log("Removed track at index $index")
			} else {
				log("Invalid index for removeTrack: $index")
			}
		}
	}
	

	suspend fun playNext() {
		ensureSession()
		runOnUiThread {
			val browser = enginerBrowser
			if (browser != null) {
				log("playNext: count=${browser.mediaItemCount}, index=${browser.currentMediaItemIndex}, hasNext=${browser.hasNextMediaItem()}")
				if (browser.hasNextMediaItem()) {
					browser.seekToNextMediaItem()
					if (browser.playbackState == Player.STATE_IDLE || browser.playbackState == Player.STATE_ENDED) {
						browser.prepare()
					}
					browser.play()
					log("Skipped to next track")
				} else {
					log("No next track to skip to")
				}
			} else {
				log("playNext: Browser is null")
			}
		}
	}

	suspend fun playPrevious() {
		ensureSession()
		runOnUiThread {
			val browser = enginerBrowser
			if (browser != null) {
				log("playPrevious: count=${browser.mediaItemCount}, index=${browser.currentMediaItemIndex}, hasPrevious=${browser.hasPreviousMediaItem()}")
				if (browser.hasPreviousMediaItem()) {
					browser.seekToPreviousMediaItem()
					if (browser.playbackState == Player.STATE_IDLE || browser.playbackState == Player.STATE_ENDED) {
						browser.prepare()
					}
					browser.play()
					log("Skipped to previous track")
				} else {
					log("No previous track to skip to")
					browser.seekTo(0)
				}
			} else {
				log("playPrevious: Browser is null")
			}
		}
	}

	suspend fun setEqualizer(gains: ReadableArray) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			val floatArray = FloatArray(gains.size())
			for (i in 0 until gains.size()) {
				floatArray[i] = gains.getDouble(i).toFloat()
			}
			bundle.putFloatArray("gains", floatArray)
			
			enginerBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_EQUALIZER, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setEqualizer command: ${floatArray.joinToString()}")
		}
	}

	suspend fun setBassBoost(strength: Int) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			bundle.putInt("strength", strength)
			
			enginerBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_BASS_BOOST, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setBassBoost command: $strength")
		}
	}

	suspend fun setRepeatMode(mode: String) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			bundle.putString("mode", mode)
			
			enginerBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_REPEAT_MODE, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setRepeatMode command: $mode")
		}
	}

	suspend fun setShuffleMode(enabled: Boolean) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			bundle.putBoolean("enabled", enabled)
			
			enginerBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_SHUFFLE_MODE, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setShuffleMode command: $enabled")
		}
	}

	suspend fun setSkipSilence(enabled: Boolean) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			bundle.putBoolean("enabled", enabled)
			
			enginerBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_SKIP_SILENCE, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setSkipSilence command: $enabled")
		}
	}

	fun startSleepTimer(seconds: Double) {
		cancelSleepTimer() // Clear any existing timer
		
		val durationMs = (seconds * 1000).toLong()
		log("Starting sleep timer for $seconds seconds ($durationMs ms)")
		
		sleepTimerHandler = Handler(Looper.getMainLooper())
		sleepTimerRunnable = Runnable {
			log("Sleep timer triggered. Pausing playback.")
			CoroutineScope(Dispatchers.Main).launch {
				pause()
				emitEvent(
					AudioProModule.EVENT_TYPE_SLEEP_TIMER_COMPLETE,
					activeTrack,
					null,
					"sleepTimerComplete"
				)
			}
		}
		
		sleepTimerHandler?.postDelayed(sleepTimerRunnable!!, durationMs)
	}
	
	fun cancelSleepTimer() {
		sleepTimerRunnable?.let {
			sleepTimerHandler?.removeCallbacks(it)
			log("Sleep timer canceled")
		}
		sleepTimerHandler = null
		sleepTimerRunnable = null
	}

	fun setNotificationButtons(buttons: ReadableArray) {
		val buttonList = mutableListOf<String>()
		for (i in 0 until buttons.size()) {
			buttons.getString(i)?.let { buttonList.add(it) }
		}
		settingNotificationButtons = buttonList
		log("Notification buttons set to: $buttonList")
		
		// Note: Notification buttons will be applied on next session creation
		// For existing sessions, user should call clear() then configure/play again
		if (::engineBrowserFuture.isInitialized && enginerBrowser != null) {
			Log.w(
				Constants.LOG_TAG,
				"Notification buttons changed mid-session. Call clear() and restart playback to apply changes."
			)
		}
	}

	fun emitCustomAction(action: String) {
		log("Custom action triggered: $action")
		val payload = Arguments.createMap().apply {
			putString("action", action)
		}
		emitEvent(
			AudioProModule.EVENT_TYPE_CUSTOM_ACTION,
			activeTrack,
			payload,
			"emitCustomAction($action)"
		)
	}
	
	suspend fun getQueue(): com.facebook.react.bridge.WritableArray {
		ensureSession()
		// We'll need a way to return this sync or async. 
		// Since we are in suspend function, we can use a CompletableDeferred or just wait?
		// But reading from browser must be on main thread? 
		// Actually getters on MediaBrowser might be thread safe if it's just local state replica?
		// "Methods of the MediaBrowser... must be called from the application thread." 
		
		val deferred = kotlinx.coroutines.CompletableDeferred<com.facebook.react.bridge.WritableArray>()
		
		runOnUiThread {
			val array = Arguments.createArray()
			enginerBrowser?.let { browser ->
				for (i in 0 until browser.mediaItemCount) {
					val item = browser.getMediaItemAt(i)
					val extras = item.mediaMetadata.extras
					if (extras != null) {
						array.pushMap(Arguments.fromBundle(extras))
					} else {
						// Fallback if no extras
						val map = Arguments.createMap()
						map.putString("title", item.mediaMetadata.title.toString())
						map.putString("url", item.mediaId) // Just using ID as placeholder
						array.pushMap(map)
					}
				}
			}
			deferred.complete(array)
		}
		
		return deferred.await()
	}

	suspend fun play(track: ReadableMap?, options: ReadableMap?) {
		ensurePreparedForNewPlayback()
		
		// If custom options are provided, parse them. Otherwise use defaults or existing?
		// For queue play, we might update options.
		// NOTE: options are conceptually for the *session* config (debug, capabilities).
		// If track is null, we assume we just want to play/resume key or provided index?
		// BUT `play` signature in RN usually implies starting something.
		
		// Logic:
		// 1. If options provided, apply them.
		// 2. If track provided -> Clear Queue, Add Track, Play. (Legacy/Single Mode)
		// 3. If track null -> Play current.
		
		val opts = if (options != null) extractPlaybackOptions(options) else null
		
		// If startTimeMs is provided, set a pending seek position
		if (opts?.startTimeMs != null) {
			flowPendingSeekPosition = opts.startTimeMs
		}

		if (opts != null) {
			log("Configured options: $opts")
		}

		runOnUiThread {
			enginerBrowser?.let { player ->
				if (track != null) {
					val item = toMediaItem(track)
					if (opts?.addTrack == true) {
						// Add to queue logic
						player.addMediaItem(item)
						player.prepare()
						
						// If we want to play this added track immediately:
						// Seek to the last item (which we just added)
						player.seekToDefaultPosition(player.mediaItemCount - 1)
						log("Added and playing track: ${track.getString("title")}")
					} else {
						// "Legacy" mode: Replace queue with this track
						player.setMediaItem(item)
						player.prepare()
						log("Playing single track (replaced queue): ${track.getString("title")}")
					}
					activeTrack = track
				} else {
					// Resume/Play existing queue
					if (player.playbackState == Player.STATE_IDLE) {
						player.prepare()
					}
					log("Playing current queue")
				}
				
				if (opts != null) {
					player.setPlaybackSpeed(opts.speed)
					player.setVolume(opts.volume)
				}
				
				// Handle autoPlay
				val shouldAutoPlay = opts?.autoPlay ?: true
				if (shouldAutoPlay) {
					player.play()
				} else {
					emitState(AudioProModule.STATE_PAUSED, 0L, 0L, "play(autoPlay=false)")
				}
			} ?: Log.w(Constants.LOG_TAG, "MediaBrowser not ready")
		}
	}

	suspend fun pause() {
		log("pause() called")
		ensureSession()
		runOnUiThread {
			enginerBrowser?.pause()
			enginerBrowser?.let {
				val pos = it.currentPosition
				val dur = it.duration.takeIf { d -> d > 0 } ?: 0L
				emitState(AudioProModule.STATE_PAUSED, pos, dur, "pause()")
			}
		}
	}

	suspend fun resume() {
		log("resume() called")
		ensureSession()
		runOnUiThread {
			enginerBrowser?.play()
			enginerBrowser?.let {
				val pos = it.currentPosition
				val dur = it.duration.takeIf { d -> d > 0 } ?: 0L
				emitState(AudioProModule.STATE_PLAYING, pos, dur, "resume()")
			}
		}
	}

	suspend fun stop() {
		log("stop() called")
		// Reset error state when explicitly stopping
		flowIsInErrorState = false
		// Reset last emitted state when stopping playback
		flowLastEmittedState = ""
		flowLastEmittedPosition = null
		flowLastEmittedDuration = null
		ensureSession()
		runOnUiThread {
			// Do not detach player listener to ensure lock screen controls still work
			// and state changes are emitted when playback is resumed from lock screen

			enginerBrowser?.stop()
			enginerBrowser?.seekTo(0)
			enginerBrowser?.let {
				// Use position 0 for STOPPED state as per logic.md contract
				val dur = it.duration.takeIf { d -> d > 0 } ?: 0L
				// Do not set currentTrack = null as STOPPED state should preserve track metadata
				emitState(AudioProModule.STATE_STOPPED, 0L, dur, "stop()")
			}
		}
		stopProgressTimer()

		// Cancel any pending seek operations
		flowPendingSeekPosition = null

		// Do not call release() as stop() should not tear down the player
		// Only clear() and unrecoverable onError() should call release()

		// Do not destroy the playback service in stop() as it should maintain the media session
		// stop() is a non-destructive state that stops playback and seeks to 0,
		// but retains lock screen info, current track, and player state
	}

	/**
	 * Resets the player to IDLE state, fully tears down the player instance,
	 * and removes all media sessions.
	 */
	fun clear() {
		resetInternal(AudioProModule.STATE_IDLE)
	}

	/**
	 * Ensures the session is ready and prepares for new playback.
	 */
	private suspend fun ensurePreparedForNewPlayback() {
		if (!hasConnectedBrowser()) {
			internalPrepareSession()
		}
		prepareForNewPlayback()
	}

	/**
	 * Shared internal function that performs the teardown and emits the correct state.
	 * Used by both clear() and error transitions.
	 */
	private fun resetInternal(finalState: String) {
		log("Reset internal, final state: $finalState")

		// Reset error state
		flowIsInErrorState = finalState == AudioProModule.STATE_ERROR
		// Reset last emitted state
		flowLastEmittedState = ""
		flowLastEmittedPosition = null
		flowLastEmittedDuration = null

		// Clear pending seek state
		flowPendingSeekPosition = null

		// Stop playback and ensure player is fully released before destroying service
		runOnUiThread {
			try {
				// First stop playback
				enginerBrowser?.stop()
				// Then detach listener to prevent callbacks during teardown
				detachPlayerListener()
				// Ensure player is released
				enginerBrowser?.release()
				log("Player successfully stopped and released")
			} catch (e: Exception) {
				Log.e(Constants.LOG_TAG, "Error stopping player", e)
			}
		}

		// Clear track and stop timers
		activeTrack = null
		stopProgressTimer()

		// Reset playback settings
		activePlaybackSpeed = 1.0f
		activeVolume = 1.0f

		// Release resources
		release()

		// Destroy the playback service directly to remove notification and tear down the media session
		destroyPlaybackService()

		// Emit final state
		emitState(finalState, 0L, 0L, "resetInternal($finalState)")
	}

	fun release() {
		runOnUiThread {
			if (::engineBrowserFuture.isInitialized) {
				MediaBrowser.releaseFuture(engineBrowserFuture)
			}
			enginerBrowser = null
			engineBrowserConnecting = false
		}
	}

	/**
	 * Explicitly destroys the AudioProPlaybackService to remove notification and tear down the media session
	 * This is the central method for destroying the service and removing the notification
	 * It should only be called from clear() and unrecoverable error scenarios, not from stop()
	 */
	fun destroyPlaybackService() {
		log("Destroying AudioProPlaybackService")
		try {
			reactContext?.let { context ->
				// Try to cancel notification directly
				try {
					val notificationManager =
						context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
					notificationManager.cancel(Constants.NOTIFICATION_ID) // Using the same NOTIFICATION_ID as in AudioProPlaybackService
				} catch (e: Exception) {
					Log.e(Constants.LOG_TAG, "Error canceling notification", e)
				}

				// Stop the service
				val intent = android.content.Intent(context, AudioProPlaybackService::class.java)
				context.stopService(intent)
			}
		} catch (e: Exception) {
			Log.e(Constants.LOG_TAG, "Error stopping service", e)
		}
	}


	suspend fun seekTo(position: Long) {
		ensureSession()
		runOnUiThread {
			performSeek(position)
		}
	}

	private fun performSeek(position: Long) {
		val dur = enginerBrowser?.duration ?: 0L
		val validPosition = when {
			position < 0 -> 0L
			position > dur -> dur
			else -> position
		}

		// Set pending seek position
		flowPendingSeekPosition = validPosition

		// Stop progress timer during seek
		stopProgressTimer()

		log("Seeking to position: $validPosition")
		enginerBrowser?.seekTo(validPosition)
	}

	suspend fun seekForward(amount: Long) {
		ensureSession()
		runOnUiThread {
			val current = enginerBrowser?.currentPosition ?: 0L
			val dur = enginerBrowser?.duration ?: 0L
			val newPos = (current + amount).coerceAtMost(dur)

			log("Seeking forward to position: $newPos")
			performSeek(newPos)
		}
	}

	suspend fun seekBack(amount: Long) {
		ensureSession()
		runOnUiThread {
			val current = enginerBrowser?.currentPosition ?: 0L
			val newPos = (current - amount).coerceAtLeast(0L)

			log("Seeking back to position: $newPos")
			performSeek(newPos)
		}
	}

	fun detachPlayerListener() {
		log("Detaching player listener")
		enginePlayerListener?.let {
			enginerBrowser?.removeListener(it)
			enginePlayerListener = null
		}
	}

	fun attachPlayerListener() {
		detachPlayerListener()

		enginePlayerListener = object : Player.Listener {
			override fun onRepeatModeChanged(repeatMode: Int) {
				val modeStr = when (repeatMode) {
					Player.REPEAT_MODE_ONE -> "ONE"
					Player.REPEAT_MODE_ALL -> "ALL"
					else -> "OFF"
				}
				log("onRepeatModeChanged: $modeStr")
				
				val params = Arguments.createMap()
				params.putString("mode", modeStr)
				
				emitEvent(
					AudioProModule.EVENT_TYPE_REPEAT_MODE_CHANGED,
					activeTrack,
					params,
					"onRepeatModeChanged"
				)
			}

			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
				log("onShuffleModeEnabledChanged: $shuffleModeEnabled")
				
				val params = Arguments.createMap()
				params.putBoolean("shuffleMode", shuffleModeEnabled)
				
				emitEvent(
					AudioProModule.EVENT_TYPE_SHUFFLE_MODE_CHANGED,
					activeTrack,
					params,
					"onShuffleModeEnabledChanged"
				)
			}

			override fun onIsPlayingChanged(isPlaying: Boolean) {
				log("onIsPlayingChanged", "isPlaying=", isPlaying)
				log(
					"onIsPlayingChanged -> currentPosition=",
					enginerBrowser?.currentPosition,
					"duration=",
					enginerBrowser?.duration
				)
				val pos = enginerBrowser?.currentPosition ?: 0L
				val dur = enginerBrowser?.duration ?: 0L

				if (isPlaying) {
					emitState(AudioProModule.STATE_PLAYING, pos, dur, "onIsPlayingChanged(true)")
					startProgressTimer()
				} else {
					emitState(AudioProModule.STATE_PAUSED, pos, dur, "onIsPlayingChanged(false)")
					stopProgressTimer()
				}
			}

			override fun onPlaybackStateChanged(state: Int) {
				log(
					"onPlaybackStateChanged",
					"state=",
					state,
					"playWhenReady=",
					enginerBrowser?.playWhenReady,
					"isPlaying=",
					enginerBrowser?.isPlaying
				)
				val pos = enginerBrowser?.currentPosition ?: 0L
				val dur = enginerBrowser?.duration ?: 0L
				val isPlayIntended = enginerBrowser?.playWhenReady == true
				val isActuallyPlaying = enginerBrowser?.isPlaying == true

				when (state) {
					Player.STATE_BUFFERING -> {
						if (isPlayIntended) {
							emitState(
								AudioProModule.STATE_LOADING,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_BUFFERING, playIntended=true)"
							)
						} else if (flowLastEmittedState == AudioProModule.STATE_PLAYING) {
							emitState(
								AudioProModule.STATE_PAUSED,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_BUFFERING, playIntended=false, wasPlaying=true)"
							)
						} else {
							log("BUFFERING with playIntended=false, but not emitting PAUSED since last emitted state was not PLAYING")
						}
					}

					Player.STATE_READY -> {
						// If there's a pending seek position, perform the seek now that the player is ready
						flowPendingSeekPosition?.let { seekPos ->
							log("Performing pending seek to $seekPos in STATE_READY")
							enginerBrowser?.seekTo(seekPos)
							// pendingSeekPosition will be cleared in onPositionDiscontinuity
						}

						if (isActuallyPlaying) {
							emitState(
								AudioProModule.STATE_PLAYING,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_READY, isPlaying=true)"
							)
							startProgressTimer()
						} else {
							emitState(
								AudioProModule.STATE_PAUSED,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_READY, isPlaying=false)"
							)
							stopProgressTimer()
						}
					}

					/**
					 * Handles track completion according to the contract in logic.md:
					 * - Native is responsible for detecting the end of a track
					 * - Native must pause the player, seek to position 0, and emit both:
					 *   - STATE_CHANGED: STOPPED
					 *   - TRACK_ENDED
				 * 
				 * Note: If repeat mode is enabled (ONE or ALL), Media3 will automatically
				 * handle the repeat, so we should NOT interfere by pausing/seeking.
				 */
				Player.STATE_ENDED -> {
					stopProgressTimer()

					// Reset error state and last emitted state
					flowIsInErrorState = false
					flowLastEmittedState = ""
					flowLastEmittedPosition = null
					flowLastEmittedDuration = null

					// Check if repeat mode is enabled - if so, let Media3 handle it
					val repeatMode = enginerBrowser?.repeatMode ?: Player.REPEAT_MODE_OFF
					if (repeatMode != Player.REPEAT_MODE_OFF) {
						// Repeat is enabled - Media3 will automatically restart playback
						// Don't pause or seek, just emit the track ended event
						log("STATE_ENDED with repeat mode $repeatMode - letting Media3 handle repeat")
						emitNotice(
							AudioProModule.EVENT_TYPE_TRACK_ENDED,
							dur,
							dur,
							"onPlaybackStateChanged(STATE_ENDED, repeat=$repeatMode)"
						)
						return
					}

					// No repeat mode - pause and reset to beginning
						// 2. Seek to position 0
						enginerBrowser?.seekTo(0)

						// 3. Cancel any pending seek operations
						flowPendingSeekPosition = null

						// 4. Emit STOPPED (stopped = loaded but at 0, not playing)
						emitState(
							AudioProModule.STATE_STOPPED,
							0L,
							dur,
							"onPlaybackStateChanged(STATE_ENDED)"
						)

						// 5. Emit TRACK_ENDED for JS
						emitNotice(
							AudioProModule.EVENT_TYPE_TRACK_ENDED,
							dur,
							dur,
							"onPlaybackStateChanged(STATE_ENDED)"
						)
					}

					Player.STATE_IDLE -> {
						stopProgressTimer()
						emitState(
							AudioProModule.STATE_STOPPED,
							0L,
							0L,
							"onPlaybackStateChanged(STATE_IDLE)"
						)
					}
				}
			}

			override fun onPositionDiscontinuity(
				oldPosition: Player.PositionInfo,
				newPosition: Player.PositionInfo,
				reason: Int
			) {
				if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
					log("Seek completed: position=${newPosition.positionMs}, reason=$reason")
					val dur = enginerBrowser?.duration ?: 0L

					val triggeredBy = if (flowPendingSeekPosition != null) {
						AudioProModule.TRIGGER_SOURCE_USER
					} else {
						AudioProModule.TRIGGER_SOURCE_SYSTEM
					}

					// Determine position for user-initiated seeks
					val pos = flowPendingSeekPosition ?: newPosition.positionMs
					flowPendingSeekPosition = null

					val payload = Arguments.createMap().apply {
						putDouble("position", pos.toDouble())
						putDouble("duration", dur.toDouble())
						putString("triggeredBy", triggeredBy)
					}

					emitEvent(
						AudioProModule.EVENT_TYPE_SEEK_COMPLETE,
						activeTrack,
						payload,
						"onPositionDiscontinuity(reason=$reason, triggeredBy=$triggeredBy)"
					)

					if (triggeredBy == AudioProModule.TRIGGER_SOURCE_USER) {
						startProgressTimer()
					}
				}
			}

			/**
			 * Handles critical errors according to the contract in logic.md:
			 * - onError() should transition to ERROR state
			 * - onError() should emit STATE_CHANGED: ERROR and PLAYBACK_ERROR
			 * - onError() should clear the player state just like clear()
			 *
			 * This method is for unrecoverable player failures that require player teardown.
			 * For non-critical errors that don't require state transition, use emitError() directly.
			 */
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				log("onMediaItemTransition", "mediaId=", mediaItem?.mediaId, "reason=", reason)
				
				if (mediaItem != null) {
					val extras = mediaItem.mediaMetadata.extras
					if (extras != null) {
						val index = enginerBrowser?.currentMediaItemIndex ?: -1
						
						val trackMap = Arguments.fromBundle(extras)
						// Add index to track map for convenience
						trackMap?.let {
							(it as? com.facebook.react.bridge.WritableMap)?.putInt("index", index)
						}
						
						activeTrack = trackMap // Update active track

						val payload = Arguments.createMap().apply {
							putInt("index", index)
						}

						emitEvent(
                            AudioProModule.EVENT_TYPE_TRACK_CHANGED, 
                            trackMap, 
                            payload, 
                            "onMediaItemTransition(reason=$reason)"
                        )
					}
				}
			}

			override fun onPlayerError(error: PlaybackException) {
				// If we're already in an error state, just log and return
				if (flowIsInErrorState) {
					log("Already in error state, ignoring additional error: ${error.message}")
					return
				}

				// Enhanced error logging for debugging
				val errorDetails = StringBuilder()
				errorDetails.append("PlaybackException: ${error.message}")
				errorDetails.append(" | Error code: ${error.errorCode}")
				errorDetails.append(" | Error code name: ${error.errorCodeName}")
				
				error.cause?.let { cause ->
					errorDetails.append(" | Cause: ${cause.javaClass.simpleName}: ${cause.message}")
				}
				
				android.util.Log.e(Constants.LOG_TAG, errorDetails.toString(), error)

				val message = error.message ?: "Unknown error"
				// First, emit PLAYBACK_ERROR event with error details
				emitError(message, 500, "onPlayerError(${error.errorCode})")

				// Then use the shared resetInternal function to:
				// 1. Clear the player state (like clear())
				// 2. Emit STATE_CHANGED: ERROR
				resetInternal(AudioProModule.STATE_ERROR)
			}
		}

		enginerBrowser?.addListener(enginePlayerListener!!)
	}

	private fun startProgressTimer() {
		stopProgressTimer()
		engineProgressHandler = Handler(Looper.getMainLooper())
		engineProgressRunnable = object : Runnable {
			override fun run() {
				val pos = enginerBrowser?.currentPosition ?: 0L
				val dur = enginerBrowser?.duration ?: 0L
				emitNotice(AudioProModule.EVENT_TYPE_PROGRESS, pos, dur, "progressTimer")
				engineProgressHandler?.postDelayed(this, settingProgressIntervalMs)
			}
		}
		engineProgressRunnable?.let {
			engineProgressHandler?.post(it)
		}
	}

	private fun stopProgressTimer() {
		engineProgressRunnable?.let { engineProgressHandler?.removeCallbacks(it) }
		engineProgressHandler = null
		engineProgressRunnable = null
	}

	private fun runOnUiThread(block: () -> Unit) {
		Handler(Looper.getMainLooper()).post(block)
	}

	private fun emitEvent(
		type: String,
		track: ReadableMap?,
		payload: WritableMap?,
		reason: String = ""
	) {
		log("emitEvent", type, "reason=", reason)
		val context = reactContext
		if (context is ReactApplicationContext) {
			val body = Arguments.createMap().apply {
				putString("type", type)

				if (track != null) {
					putMap("track", track.toHashMap().let { Arguments.makeNativeMap(it) })
				} else {
					putNull("track")
				}

				if (payload != null) {
					putMap("payload", payload)
				}
			}

			context
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
				.emit(AudioProModule.EVENT_NAME, body)
		} else {
			Log.w(
				Constants.LOG_TAG,
				"Context is not an instance of ReactApplicationContext"
			)
		}
	}

	private fun emitState(state: String, position: Long, duration: Long, reason: String = "") {
		val sanitizedPosition = if (position < 0) 0L else position
		val sanitizedDuration = if (duration < 0) 0L else duration
		log(
			"emitState",
			state,
			"position=",
			sanitizedPosition,
			"duration=",
			sanitizedDuration,
			"reason=",
			reason
		)
		// Don't emit PAUSED if we've already emitted STOPPED (catch slow listener emit)
		if (state == AudioProModule.STATE_PAUSED && flowLastEmittedState == AudioProModule.STATE_STOPPED) {
			log("Ignoring PAUSED state after STOPPED")
			return
		}

		// Don't emit STOPPED if we're in an error state
		if (state == AudioProModule.STATE_STOPPED && flowIsInErrorState) {
			log("Ignoring STOPPED state after ERROR")
			return
		}

		// Filter out duplicate state emissions
		// This prevents rapid-fire transitions of the same state being emitted repeatedly
		if (state == flowLastEmittedState) {
			val lastPosition = flowLastEmittedPosition
			val lastDuration = flowLastEmittedDuration
			if (lastPosition != null && lastDuration != null) {
				val positionDelta = abs(sanitizedPosition - lastPosition)
				val durationChanged = sanitizedDuration != lastDuration
				val hasMeaningfulPositionChange = positionDelta >= DUPLICATE_POSITION_EPSILON_MS

				if (!durationChanged && !hasMeaningfulPositionChange) {
					log("Ignoring duplicate $state state emission (position/duration unchanged within epsilon)")
					return
				}
			}
		}

		val index = enginerBrowser?.currentMediaItemIndex ?: -1
		val payload = Arguments.createMap().apply {
			putString("state", state)
			putDouble("position", sanitizedPosition.toDouble())
			putDouble("duration", sanitizedDuration.toDouble())
			putInt("index", index)
		}
		emitEvent(AudioProModule.EVENT_TYPE_STATE_CHANGED, activeTrack, payload, reason)

		// Track the last emitted state
		flowLastEmittedState = state
		flowLastEmittedPosition = sanitizedPosition
		flowLastEmittedDuration = sanitizedDuration
		// Record time of this state emission
		flowLastStateEmittedTimeMs = System.currentTimeMillis()
	}

	private fun emitNotice(eventType: String, position: Long, duration: Long, reason: String = "") {
		// Sanitize negative values
		val sanitizedPosition = if (position < 0) 0L else position
		val sanitizedDuration = if (duration < 0) 0L else duration

		val payload = Arguments.createMap().apply {
			putDouble("position", sanitizedPosition.toDouble())
			putDouble("duration", sanitizedDuration.toDouble())
		}
		emitEvent(eventType, activeTrack, payload, reason)
	}

	/**
	 * Emits a PLAYBACK_ERROR event without transitioning to the ERROR state.
	 * Use this for non-critical errors that don't require player teardown.
	 *
	 * According to the contract in logic.md:
	 * - PLAYBACK_ERROR and ERROR state are separate and must not be conflated
	 * - PLAYBACK_ERROR can be emitted with or without a corresponding state change
	 * - Useful for soft errors (e.g., image fetch failed, headers issue, non-fatal network retry)
	 */
	private fun emitError(message: String, code: Int, reason: String = "") {
		val payload = Arguments.createMap().apply {
			putString("error", message)
			putInt("errorCode", code)
		}
		emitEvent(AudioProModule.EVENT_TYPE_PLAYBACK_ERROR, activeTrack, payload, reason)
	}

	fun emitNext(reason: String = "") {
		val payload = Arguments.createMap().apply {
			putString("state", flowLastEmittedState)
		}
		emitEvent(AudioProModule.EVENT_TYPE_REMOTE_NEXT, activeTrack, payload, reason)
	}

	fun emitPrev(reason: String = "") {
		val payload = Arguments.createMap().apply {
			putString("state", flowLastEmittedState)
		}
		emitEvent(AudioProModule.EVENT_TYPE_REMOTE_PREV, activeTrack, payload, reason)
	}

	private fun emitEventToJS(event: WritableMap) {
		val context = reactContext
		if (context is ReactApplicationContext && context.hasActiveCatalystInstance()) {
			context
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
				.emit(AudioProModule.EVENT_NAME, event)
		}
	}

	suspend fun setPlaybackSpeed(speed: Float) {
		ensureSession()
		activePlaybackSpeed = speed
		runOnUiThread {
			log("Setting playback speed to", speed)
			enginerBrowser?.setPlaybackSpeed(speed)

			val payload = Arguments.createMap().apply {
				putDouble("speed", speed.toDouble())
			}
			emitEvent(
				AudioProModule.EVENT_TYPE_PLAYBACK_SPEED_CHANGED,
				activeTrack,
				payload,
				"setPlaybackSpeed($speed)"
			)
		}
	}

	suspend fun setVolume(volume: Float) {
		ensureSession()
		activeVolume = volume
		runOnUiThread {
			log("Setting volume to", volume)
			enginerBrowser?.setVolume(volume)
		}
	}


	suspend fun seekBy(offsetMs: Long) {
		ensureSession()
		runOnUiThread {
			val current = enginerBrowser?.currentPosition ?: 0L
			val duration = enginerBrowser?.duration ?: 0L
			val newPos = (current + offsetMs).coerceIn(0L, duration)
			log("SeekBy offset=$offsetMs current=$current new=$newPos")
			performSeek(newPos)
		}
	}

	/**
	 * Helper to extract header maps from a ReadableMap.
	 */
	private fun extractHeaders(headersMap: ReadableMap?): Map<String, String>? {
		if (headersMap == null) return null

		val headerMap = mutableMapOf<String, String>()
		val iterator = headersMap.keySetIterator()
		while (iterator.hasNextKey()) {
			val key = iterator.nextKey()
			val value = headersMap.getString(key)
			if (value != null) {
				headerMap[key] = value
			}
		}
		return if (headerMap.isNotEmpty()) headerMap else null
	}
}
