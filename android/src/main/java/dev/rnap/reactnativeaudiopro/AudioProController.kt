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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

import android.app.PendingIntent
import java.util.ArrayList

object AudioProController {
	private const val DUPLICATE_POSITION_EPSILON_MS = 250L

	private var reactContext: ReactApplicationContext? = null
	private lateinit var engineBrowserFuture: ListenableFuture<MediaBrowser>
	private var engineBrowser: MediaBrowser? = null
	private var sessionDeferred: CompletableDeferred<MediaBrowser>? = null
	private val sessionMutex = Mutex()
	private var engineProgressHandler: Handler? = null
	private var engineProgressRunnable: Runnable? = null
	private var enginePlayerListener: Player.Listener? = null
	
	// ─────────────────────────────────────────────────────────────
	// Batched Event System (Media3 onEvents pattern)
	// ─────────────────────────────────────────────────────────────
	// Instead of emitting events from each individual callback,
	// we defer emissions until onEvents() is called. This ensures
	// a single batched emission after all state changes settle.
	
	/** Pending state to emit (null = no state change pending) */
	private var pendingState: String? = null
	private var pendingStatePosition: Long = 0L
	private var pendingStateDuration: Long = 0L
	private var pendingStateReason: String = ""
	
	/** Flag: should we emit TRACK_ENDED in onEvents? */
	private var pendingTrackEnded: Boolean = false
	private var pendingTrackEndedDuration: Long = 0L
	
	/** Flag: player error occurred this batch */
	private var pendingError: PlaybackException? = null
	
	// Sleep Timer
	private var sleepTimerHandler: Handler? = null
	private var sleepTimerRunnable: Runnable? = null
	
	/**
	 * Returns the current player duration in ms, safely handling null,
	 * C.TIME_UNSET, and negative values that Media3 can return before
	 * the timeline is resolved.
	 */
	private fun safeDuration(): Long {
		val raw = engineBrowser?.duration ?: return 0L
		return if (raw != C.TIME_UNSET && raw > 0) raw else 0L
	}

	// ─────────────────────────────────────────────────────────────
	// Batched Event Emission Helpers
	// ─────────────────────────────────────────────────────────────
	
	/**
	 * Sets a pending state to be emitted in the next onEvents() call.
	 * Does NOT emit immediately - batching prevents UI flicker.
	 */
	private fun setPendingState(state: String, position: Long, duration: Long, reason: String) {
		log("setPendingState: $state (reason=$reason)")
		pendingState = state
		pendingStatePosition = position
		pendingStateDuration = duration
		pendingStateReason = reason
	}
	
	/**
	 * Clears all pending batch state. Called after emitting.
	 */
	private fun clearPendingBatch() {
		pendingState = null
		pendingStatePosition = 0L
		pendingStateDuration = 0L
		pendingStateReason = ""
		pendingTrackEnded = false
		pendingTrackEndedDuration = 0L
		pendingError = null
	}
	
	/**
	 * Emits all pending batched events. Called from onEvents().
	 * This is the ONLY place state changes should be emitted to JS
	 * (except for events that need immediate emission like SEEK_COMPLETE).
	 */
	private fun emitBatchedEvents(player: Player) {
		// Handle pending error first (takes priority)
		pendingError?.let { error ->
			val message = error.message ?: "Unknown error"
			val jsErrorCode = mapMedia3ErrorCode(error.errorCode)
			val recoverable = isRecoverableError(error.errorCode)
			val cause = error.cause?.let { "${it.javaClass.simpleName}: ${it.message}" }
			emitError(message, jsErrorCode, recoverable, cause, "onEvents(pendingError)")
			
			if (!recoverable) {
				resetInternal(AudioProModule.STATE_ERROR)
			}
			clearPendingBatch()
			return
		}
		
		// Emit pending state change
		pendingState?.let { state ->
			emitState(state, pendingStatePosition, pendingStateDuration, "onEvents(${pendingStateReason})")
			
			// Manage progress timer based on state
			when (state) {
				AudioProModule.STATE_PLAYING -> startProgressTimer()
				AudioProModule.STATE_PAUSED,
				AudioProModule.STATE_STOPPED,
				AudioProModule.STATE_ERROR -> stopProgressTimer()
			}
		}
		
		// Emit TRACK_ENDED if pending
		if (pendingTrackEnded) {
			emitNotice(
				AudioProModule.EVENT_TYPE_TRACK_ENDED,
				pendingTrackEndedDuration,
				pendingTrackEndedDuration,
				"onEvents(trackEnded)"
			)
		}
		
		clearPendingBatch()
	}

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
	private var flowIsRestorationSeek: Boolean = false
	private var flowPendingSeekIndex: Int? = null // For deferred seeks when queue is loading

	private var settingDebug: Boolean = false
	private var settingDebugIncludesProgress: Boolean = false
	private var settingProgressIntervalMs: Long = 1000
	var settingAudioContentType: Int = C.AUDIO_CONTENT_TYPE_MUSIC
	var settingNotificationButtons: List<String> = listOf("PREV", "NEXT")
	var settingSkipIntervalMs: Long = 30000L
	var settingCacheEnabled: Boolean = true

	fun configure(options: ReadableMap) {
		if (options.hasKey("debug")) {
			settingDebug = options.getBoolean("debug")
		}
		if (options.hasKey("debugIncludesProgress")) {
			settingDebugIncludesProgress = options.getBoolean("debugIncludesProgress")
		}
		if (options.hasKey("progressIntervalMs")) {
			settingProgressIntervalMs = options.getDouble("progressIntervalMs").toLong()
		}
		if (options.hasKey("audioContentType")) {
			settingAudioContentType = options.getInt("audioContentType")
		}
		if (options.hasKey("skipIntervalMs")) {
			settingSkipIntervalMs = options.getDouble("skipIntervalMs").toLong()
		}
		if (options.hasKey("cacheEnabled")) {
			settingCacheEnabled = options.getBoolean("cacheEnabled")
		}
		if (options.hasKey("maxCacheSize")) {
			val size = options.getDouble("maxCacheSize").toLong()
			AudioProCache.setMaxCacheSize(size)
			log("Configured maxCacheSize: $size bytes")
		}
		if (options.hasKey("skipSilence")) {
			val enabled = options.getBoolean("skipSilence")
			CoroutineScope(Dispatchers.Main).launch {
				setSkipSilence(enabled)
			}
		}
		
		log("Configured AudioPro: debug=$settingDebug, cache=$settingCacheEnabled")
	}

	/**
	 * Emit current state and progress to JS so the internal store syncs with native.
	 * Called when JS app resumes after being killed but native service survived.
	 */
	fun syncFromNative() {
		runOnUiThread {
			val browser = engineBrowser ?: run {
				log("syncFromNative: no active browser, skipping")
				return@runOnUiThread
			}
			
			val position = browser.currentPosition.coerceAtLeast(0L)
			val duration = browser.duration.let { if (it > 0) it else 0L }
			val buffered = browser.bufferedPosition.coerceAtLeast(0L)
			
			// Determine current state from player
			val state = when {
				browser.playbackState == androidx.media3.common.Player.STATE_IDLE -> AudioProModule.STATE_IDLE
				browser.playbackState == androidx.media3.common.Player.STATE_ENDED -> AudioProModule.STATE_STOPPED
				browser.isPlaying -> AudioProModule.STATE_PLAYING
				browser.playWhenReady && browser.playbackState == androidx.media3.common.Player.STATE_BUFFERING -> AudioProModule.STATE_LOADING
				else -> AudioProModule.STATE_PAUSED
			}
			
			log("syncFromNative: state=$state, position=$position, duration=$duration")
			
			// Force emit state (bypass duplicate check by temporarily clearing lastEmittedState)
			flowLastEmittedState = ""
			emitState(state, position, duration, "syncFromNative")
			
			// Emit progress
			emitProgress(position, duration, buffered, "syncFromNative")
			
			// Emit track changed if we have an active track
			activeTrack?.let { trackMap ->
				val index = browser.currentMediaItemIndex
				val payload = Arguments.createMap().apply {
					putInt("index", index)
				}
				emitEvent(AudioProModule.EVENT_TYPE_TRACK_CHANGED, trackMap, payload, "syncFromNative")
			}
		}
	}

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
		if (engineBrowser?.isConnected == true) return

		sessionMutex.withLock {
			// Double-check inside lock
			if (engineBrowser?.isConnected == true) return

			// If another coroutine is already connecting, wait on its deferred
			sessionDeferred?.let { deferred ->
				try {
					deferred.await()
					return
				} catch (_: Exception) {
					// Previous attempt failed, fall through to retry
				}
			}

			internalPrepareSession()
		}
	}

	private fun hasConnectedBrowser(): Boolean {
		return engineBrowser?.isConnected == true
	}

	private fun handleBrowserDisconnected(controller: MediaController) {
		runOnUiThread {
			// Only clear if we're dealing with the active controller reference
			if (engineBrowser == controller) {
				detachPlayerListener()
				stopProgressTimer()

				// Only emit STOPPED if we're not in the middle of reconnecting.
				// If sessionDeferred is set, a new connection is pending - skip STOPPED
				// to avoid resetting position in the JS store during reconnection.
				if (sessionDeferred == null) {
					Log.i(Constants.LOG_TAG, "[DISCONNECT] MediaBrowser disconnected – emitting STOPPED")
					emitState(AudioProModule.STATE_STOPPED, 0L, 0L, "browserDisconnected")
				} else {
					Log.i(Constants.LOG_TAG, "[DISCONNECT] MediaBrowser disconnected but reconnection pending – skipping STOPPED emit")
				}

				if (::engineBrowserFuture.isInitialized) {
					MediaBrowser.releaseFuture(engineBrowserFuture)
				}
				engineBrowser = null
				sessionDeferred = null
			} else {
				log(
					"Ignoring disconnect from stale MediaBrowser instance. Active=$engineBrowser, disconnected=$controller"
				)
			}
		}
	}

	private suspend fun internalPrepareSession() {
		val context = reactContext ?: run {
			log("React context unavailable, skipping MediaBrowser initialization")
			return
		}

		val deferred = CompletableDeferred<MediaBrowser>()
		sessionDeferred = deferred

		try {
			log("Preparing MediaBrowser session")
			val token = SessionToken(
				context,
				ComponentName(context, AudioProPlaybackService::class.java)
			)

			engineBrowserFuture = MediaBrowser.Builder(context, token)
				.setListener(engineBrowserConnectionListener)
				.buildAsync()

			val browser = engineBrowserFuture.await()
			engineBrowser = browser
			attachPlayerListener()
			deferred.complete(browser)
			log("MediaBrowser is ready")
		} catch (e: Exception) {
			log("Failed to connect MediaBrowser: ${e.message}")
			deferred.completeExceptionally(e)
			sessionDeferred = null
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
			engineBrowser?.pause()
		}

		stopProgressTimer()

		flowPendingSeekPosition = null
		flowIsRestorationSeek = false
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


	
	suspend fun addMediaItems(tracks: com.facebook.react.bridge.ReadableArray) {
		ensureSession()
		val items = ArrayList<MediaItem>()
		for (i in 0 until tracks.size()) {
			tracks.getMap(i)?.let {
				items.add(toMediaItem(it))
			}
		}
		runOnUiThread {
			engineBrowser?.addMediaItems(items)
			log("Added ${items.size} media items to queue")
		}
	}
	
	suspend fun addMediaItems(track: ReadableMap) {
		ensureSession()
		val item = toMediaItem(track)
		runOnUiThread {
			engineBrowser?.addMediaItem(item)
			log("Added media item to queue: ${track.getString("title")}")
		}
	}

	/**
	 * Insert media items at a specific position in the queue.
	 * Media3 equivalent: player.addMediaItems(index, items)
	 */
	suspend fun addMediaItemsAt(index: Int, tracks: com.facebook.react.bridge.ReadableArray) {
		ensureSession()
		val items = ArrayList<MediaItem>()
		for (i in 0 until tracks.size()) {
			tracks.getMap(i)?.let {
				items.add(toMediaItem(it))
			}
		}
		runOnUiThread {
			engineBrowser?.let { player ->
				val safeIndex = index.coerceIn(0, player.mediaItemCount)
				player.addMediaItems(safeIndex, items)
				log("Inserted ${items.size} media items at index $safeIndex")
			}
		}
	}

	/**
	 * Remove a range of media items from the queue.
	 * Media3 equivalent: player.removeMediaItems(fromIndex, toIndex)
	 * @param fromIndex Start index (inclusive)
	 * @param toIndex End index (exclusive)
	 */
	suspend fun removeMediaItems(fromIndex: Int, toIndex: Int) {
		ensureSession()
		runOnUiThread {
			engineBrowser?.let { player ->
				if (fromIndex >= 0 && toIndex <= player.mediaItemCount && fromIndex < toIndex) {
					player.removeMediaItems(fromIndex, toIndex)
					log("Removed media items from index $fromIndex to $toIndex")
				} else {
					log("Invalid range for removeMediaItems: $fromIndex to $toIndex (count=${player.mediaItemCount})")
				}
			}
		}
	}

	/**
	 * Move a media item from one position to another in the queue.
	 * Media3 equivalent: player.moveMediaItem(currentIndex, newIndex)
	 */
	suspend fun moveMediaItem(fromIndex: Int, toIndex: Int) {
		ensureSession()
		runOnUiThread {
			engineBrowser?.let { player ->
				if (fromIndex >= 0 && fromIndex < player.mediaItemCount &&
					toIndex >= 0 && toIndex < player.mediaItemCount) {
					player.moveMediaItem(fromIndex, toIndex)
					log("Moved media item from index $fromIndex to $toIndex")
				} else {
					log("Invalid indices for moveMediaItem: $fromIndex -> $toIndex (count=${player.mediaItemCount})")
				}
			}
		}
	}

	/**
	 * Set media items (replaces entire queue).
	 * Media3 equivalent: player.setMediaItems(items)
	 */
	suspend fun setMediaItems(tracks: com.facebook.react.bridge.ReadableArray) {
		ensureSession()
		val items = ArrayList<MediaItem>()
		for (i in 0 until tracks.size()) {
			tracks.getMap(i)?.let {
				items.add(toMediaItem(it))
			}
		}
		runOnUiThread {
			engineBrowser?.setMediaItems(items)
			log("Set ${items.size} media items")
		}
	}

	suspend fun clearMediaItems() {
		ensureSession()
		runOnUiThread {
			engineBrowser?.stop()
			engineBrowser?.clearMediaItems()
			log("Stopped playback and cleared media items")
		}
	}


	suspend fun seekToMediaItem(index: Int) {
		ensureSession()
		runOnUiThread {
			engineBrowser?.let { player ->
				if (index >= 0 && index < player.mediaItemCount) {
					player.seekToDefaultPosition(index)
					log("seekToMediaItem: Switching to media item at index $index")
				} else {
					log("seekToMediaItem: Index $index out of bounds (count=${player.mediaItemCount}), deferring seek")
					flowPendingSeekIndex = index
					flowPendingSeekPosition = null // reset specific position if just seekToMediaItem
				}
			}
		}
	}

	suspend fun seekToMediaItemWithPosition(index: Int, position: Long) {
		ensureSession()
		runOnUiThread {
			engineBrowser?.let { player ->
				if (index >= 0 && index < player.mediaItemCount) {
					// Existing logic for valid index
					log("seekToMediaItemWithPosition: Switching to media item at index $index")
					
					flowIsRestorationSeek = true
					flowPendingSeekPosition = position

					player.seekToDefaultPosition(index)

					if (player.playbackState == Player.STATE_READY) {
						Log.i(Constants.LOG_TAG, "[RESTORE] Player already READY, performing immediate seek to $position")
						log("seekToMediaItemWithPosition: Player already READY, performing immediate seek to $position")
						flowIsRestorationSeek = false
						player.seekTo(position)
						
						val dur = safeDuration()
						val isPlaying = player.isPlaying
						val buffered = player.bufferedPosition
						
						emitProgress(position, dur, buffered, "seekToMediaItemWithPosition(immediate)")
						val state = if (isPlaying) AudioProModule.STATE_PLAYING else AudioProModule.STATE_PAUSED
						emitState(state, position, dur, "seekToMediaItemWithPosition(immediate, state=$state)")
					} else if (player.playbackState == Player.STATE_IDLE) {
						Log.i(Constants.LOG_TAG, "[RESTORE] Player IDLE, emitting LOADING state and calling prepare()")
						log("seekToMediaItemWithPosition: Player IDLE, emitting LOADING state and calling prepare()")
						
						emitState(AudioProModule.STATE_LOADING, 0L, 0L, "seekToMediaItemWithPosition(prepare)")
						// Ensure playWhenReady is false before prepare() to prevent auto-play.
						// This is a restoration seek - user should explicitly press play.
						player.playWhenReady = false
						player.prepare()
					}
				} else {
					log("seekToMediaItemWithPosition: Index $index out of bounds (count=${player.mediaItemCount}), deferring seek")
					flowPendingSeekIndex = index
					flowPendingSeekPosition = position
					flowIsRestorationSeek = true // Mark as restoration seek
				}
			}
		}
	}

	suspend fun removeMediaItem(index: Int) {
		ensureSession()
		runOnUiThread {
			if (index >= 0 && index < (engineBrowser?.mediaItemCount ?: 0)) {
				engineBrowser?.removeMediaItem(index)
				log("Removed media item at index $index")
			} else {
				log("Invalid index for removeMediaItem: $index")
			}
		}
	}
	

	

	suspend fun seekToNextMediaItem() {
		ensureSession()
		runOnUiThread {
			val browser = engineBrowser
			if (browser != null) {
				log("seekToNextMediaItem: count=${browser.mediaItemCount}, index=${browser.currentMediaItemIndex}, hasNext=${browser.hasNextMediaItem()}")
				if (browser.hasNextMediaItem()) {
					browser.seekToNextMediaItem()
					if (browser.playbackState == Player.STATE_IDLE || browser.playbackState == Player.STATE_ENDED) {
						browser.prepare()
					}
					browser.play()
					log("Seeked to next media item")
				} else {
					log("No next media item to seek to")
				}
			} else {
				log("seekToNextMediaItem: Browser is null")
			}
		}
	}

	suspend fun seekToPreviousMediaItem() {
		ensureSession()
		runOnUiThread {
			val browser = engineBrowser
			if (browser != null) {
				log("seekToPreviousMediaItem: count=${browser.mediaItemCount}, index=${browser.currentMediaItemIndex}, hasPrevious=${browser.hasPreviousMediaItem()}")
				if (browser.hasPreviousMediaItem()) {
					browser.seekToPreviousMediaItem()
					if (browser.playbackState == Player.STATE_IDLE || browser.playbackState == Player.STATE_ENDED) {
						browser.prepare()
					}
					browser.play()
					log("Seeked to previous media item")
				} else {
					log("No previous media item to seek to")
					browser.seekTo(0)
				}
			} else {
				log("seekToPreviousMediaItem: Browser is null")
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
			
			engineBrowser?.sendCustomCommand(
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
			
			engineBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_BASS_BOOST, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setBassBoost command: $strength")
		}
	}

	suspend fun setRepeatMode(mode: String) {
		ensureSession()
		runOnUiThread {
			// Use standard Player method instead of custom session command
			val repeatMode = when (mode.lowercase()) {
				"off" -> Player.REPEAT_MODE_OFF
				"one" -> Player.REPEAT_MODE_ONE
				"all" -> Player.REPEAT_MODE_ALL
				else -> Player.REPEAT_MODE_OFF
			}
			engineBrowser?.repeatMode = repeatMode
			log("Set repeatMode: $mode -> $repeatMode")
		}
	}

	suspend fun setShuffleModeEnabled(enabled: Boolean) {
		ensureSession()
		runOnUiThread {
			// Use standard Player method instead of custom session command
			engineBrowser?.shuffleModeEnabled = enabled
			log("Set shuffleModeEnabled: $enabled")
		}
	}

	suspend fun setSkipSilence(enabled: Boolean) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle()
			bundle.putBoolean("enabled", enabled)
			
			engineBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_SET_SKIP_SILENCE, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent setSkipSilence command: $enabled")
		}
	}

	/**
	 * Updates the notification button states (like/dislike/bookmark).
	 * Call this when the user interacts with like/dislike/bookmark or when track changes.
	 * The notification will update to reflect the new states.
	 */
	suspend fun updateNotificationState(liked: Boolean, disliked: Boolean = false, bookmarked: Boolean = false) {
		ensureSession()
		runOnUiThread {
			val bundle = android.os.Bundle().apply {
				putBoolean("liked", liked)
				putBoolean("disliked", disliked)
				putBoolean("bookmarked", bookmarked)
			}
			
			engineBrowser?.sendCustomCommand(
				SessionCommand(Constants.CUSTOM_COMMAND_UPDATE_NOTIFICATION_STATE, android.os.Bundle.EMPTY),
				bundle
			)
			log("Sent updateNotificationState command: liked=$liked, disliked=$disliked, bookmarked=$bookmarked")
		}
	}

	suspend fun updateTrack(index: Int, track: ReadableMap) {
		ensureSession()
		runOnUiThread {
			engineBrowser?.let { browser ->
				if (index >= 0 && index < browser.mediaItemCount) {
					val item = toMediaItem(track)
					browser.replaceMediaItem(index, item)
					log("UpdateTrack: Replaced item at index $index with ${item.mediaMetadata.title}")
				} else {
					log("UpdateTrack: Invalid index $index")
				}
			}
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
		if (::engineBrowserFuture.isInitialized && engineBrowser != null) {
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
	
	suspend fun getMediaItems(): com.facebook.react.bridge.WritableArray {
		ensureSession()
		// We'll need a way to return this sync or async. 
		// Since we are in suspend function, we can use a CompletableDeferred or just wait?
		// But reading from browser must be on main thread? 
		// Actually getters on MediaBrowser might be thread safe if it's just local state replica?
		// "Methods of the MediaBrowser... must be called from the application thread." 
		
		val deferred = kotlinx.coroutines.CompletableDeferred<com.facebook.react.bridge.WritableArray>()
		
		runOnUiThread {
			val array = Arguments.createArray()
			engineBrowser?.let { browser ->
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
		if (track != null) {
			ensurePreparedForNewPlayback()
		} else {
			ensureSession()
		}
		
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
			engineBrowser?.let { player ->
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
			engineBrowser?.pause()
			engineBrowser?.let {
				val pos = it.currentPosition
				val dur = safeDuration()
				emitState(AudioProModule.STATE_PAUSED, pos, dur, "pause()")
			}
		}
	}

	suspend fun resume() {
		log("resume() called")
		ensureSession()
		runOnUiThread {
			engineBrowser?.play()
			engineBrowser?.let {
				val pos = it.currentPosition
				val dur = safeDuration()
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

			engineBrowser?.stop()
			engineBrowser?.seekTo(0)
			engineBrowser?.let {
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
		flowIsRestorationSeek = false

		// Clear track and stop timers
		activeTrack = null
		stopProgressTimer()

		// Reset playback settings
		activePlaybackSpeed = 1.0f
		activeVolume = 1.0f

		// Stop playback, release resources, and clean up in a single UI-thread block
		runOnUiThread {
			try {
				// First stop playback
				engineBrowser?.stop()
				// Then detach listener to prevent callbacks during teardown
				detachPlayerListener()
				// Ensure player is released
				engineBrowser?.release()
				log("Player successfully stopped and released")
			} catch (e: Exception) {
				Log.e(Constants.LOG_TAG, "Error stopping player", e)
			}

			// Release the browser future and clear references
			if (::engineBrowserFuture.isInitialized) {
				MediaBrowser.releaseFuture(engineBrowserFuture)
			}
			engineBrowser = null
			sessionDeferred = null
		}

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
			engineBrowser = null
			sessionDeferred = null
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
		val dur = safeDuration()
		val validPosition = if (dur > 0) {
			when {
				position < 0 -> 0L
				position > dur -> dur
				else -> position
			}
		} else {
			position.coerceAtLeast(0L)
		}

		// Set pending seek position
		flowPendingSeekPosition = validPosition

		// Stop progress timer during seek
		stopProgressTimer()

		log("Seeking to position: $validPosition")
		engineBrowser?.seekTo(validPosition)
	}

	suspend fun seekForward(amount: Long) {
		ensureSession()
		runOnUiThread {
			val current = engineBrowser?.currentPosition ?: 0L
			val dur = safeDuration()
			val newPos = (current + amount).coerceAtMost(dur)

			log("Seeking forward to position: $newPos")
			performSeek(newPos)
		}
	}

	suspend fun seekBack(amount: Long) {
		ensureSession()
		runOnUiThread {
			val current = engineBrowser?.currentPosition ?: 0L
			val newPos = (current - amount).coerceAtLeast(0L)

			log("Seeking back to position: $newPos")
			performSeek(newPos)
		}
	}

	fun detachPlayerListener() {
		log("Detaching player listener")
		enginePlayerListener?.let {
			engineBrowser?.removeListener(it)
			enginePlayerListener = null
		}
	}

	fun attachPlayerListener() {
		detachPlayerListener()

		enginePlayerListener = object : Player.Listener {
			override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
				super.onTimelineChanged(timeline, reason)
				log("onTimelineChanged: reason=$reason, windowCount=${timeline.windowCount}")

				// Emit QUEUE_CHANGED when playlist is modified
				if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
					val payload = Arguments.createMap().apply {
						putInt("size", timeline.windowCount)
						putInt("currentIndex", engineBrowser?.currentMediaItemIndex ?: -1)
					}
					emitEvent(
						AudioProModule.EVENT_TYPE_QUEUE_CHANGED,
						activeTrack,
						payload,
						"onTimelineChanged(PLAYLIST_CHANGED)"
					)
				}

				// Handle deferred seek (e.g. restoration where addToQueue hasn't finished yet)
				flowPendingSeekIndex?.let { index ->
					if (index >= 0 && index < timeline.windowCount) {
						log("onTimelineChanged: Found pending seek to index $index, executing now")
						
						val position = flowPendingSeekPosition
						flowPendingSeekIndex = null // Clear pending index
						
						if (position != null) {
							// Was a skipToWithSeek
							// We can just re-call skipToWithSeek (it's safe as we're on UI thread)
							// Execute logic directly:
							engineBrowser?.let { player ->
								flowIsRestorationSeek = true
								player.seekToDefaultPosition(index)
								
								if (player.playbackState == Player.STATE_IDLE) {
									player.prepare()
								}
								// STATE_READY listener will handle the pending position seek
							}
						} else {
							// Was a normal skipTo
							engineBrowser?.seekToDefaultPosition(index)
						}
					}
				}
			}
			
			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				super.onAudioSessionIdChanged(audioSessionId)
				log("onAudioSessionIdChanged: $audioSessionId")
				
				if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
					val payload = Arguments.createMap().apply {
						putInt("audioSessionId", audioSessionId)
					}
					emitEvent(
						AudioProModule.EVENT_TYPE_AUDIO_SESSION_CHANGED,
						activeTrack,
						payload,
						"onAudioSessionIdChanged"
					)
				}
			}

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
					engineBrowser?.currentPosition,
					"duration=",
					engineBrowser?.duration,
					"pendingSeek=",
					flowPendingSeekPosition
				)
				// Use pending seek position if available to prevent UI jumping back
				val pos = flowPendingSeekPosition ?: engineBrowser?.currentPosition ?: 0L
				val dur = safeDuration()

				if (isPlaying) {
					// Defer emission to onEvents() for batching
					setPendingState(AudioProModule.STATE_PLAYING, pos, dur, "onIsPlayingChanged(true)")
				} else {
					// During a seek, Media3 briefly sets isPlaying=false while it
					// rebuffers at the new position. Emitting PAUSED here would
					// cause the UI play/pause icon to flicker. Suppress it when a
					// seek is in-flight (flowPendingSeekPosition != null) AND the
					// player still intends to play (playWhenReady=true).
					val isSeeking = flowPendingSeekPosition != null
					val playIntended = engineBrowser?.playWhenReady == true
					if (isSeeking && playIntended) {
						log("onIsPlayingChanged(false) suppressed — seek in progress with playWhenReady=true")
					} else {
						// Defer emission to onEvents() for batching
						setPendingState(AudioProModule.STATE_PAUSED, pos, dur, "onIsPlayingChanged(false)")
					}
				}
			}

			override fun onPlaybackStateChanged(state: Int) {
				log(
					"onPlaybackStateChanged",
					"state=",
					state,
					"playWhenReady=",
					engineBrowser?.playWhenReady,
					"isPlaying=",
					engineBrowser?.isPlaying
				)
				// Use pending seek position if available to prevent UI jumping back
				val pos = flowPendingSeekPosition ?: engineBrowser?.currentPosition ?: 0L
				val dur = safeDuration()
				val isPlayIntended = engineBrowser?.playWhenReady == true
				val isActuallyPlaying = engineBrowser?.isPlaying == true

				// Track whether a user-initiated seek is in-flight so we can
				// suppress transient state emissions that cause UI flicker.
				val isSeeking = flowPendingSeekPosition != null

				when (state) {
					Player.STATE_BUFFERING -> {
						// During a seek, Media3 transitions through BUFFERING briefly.
						// Emitting LOADING here would flash the UI; suppress it when a
						// seek is in-flight and the player still intends to play.
						if (isSeeking && isPlayIntended) {
							log("STATE_BUFFERING suppressed — seek in progress with playWhenReady=true")
						} else if (isPlayIntended) {
							// Defer emission to onEvents() for batching
							setPendingState(
								AudioProModule.STATE_LOADING,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_BUFFERING, playIntended=true)"
							)
						} else if (flowLastEmittedState == AudioProModule.STATE_PLAYING) {
							// Defer emission to onEvents() for batching
							setPendingState(
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
						val dur = safeDuration()
						val pos = engineBrowser?.currentPosition ?: 0L
						
						log("STATE_READY: duration=$dur, position=$pos, pendingSeek=$flowPendingSeekPosition, isRestoration=$flowIsRestorationSeek")
						
						// Handle pending seek from skipToWithSeek (restoration)
						flowPendingSeekPosition?.let { seekPos ->
							log("STATE_READY: Performing pending seek to ${seekPos}ms (duration=$dur)")
							
							// Clear restoration flag BEFORE seeking so the resulting
							// onPositionDiscontinuity fires normally as a SEEK_COMPLETE
							flowIsRestorationSeek = false
							
							// Perform the actual seek to the saved position
							engineBrowser?.seekTo(seekPos)
							
							// Emit PROGRESS so UI shows correct position+duration immediately
							// (This is immediate, not batched, as it's for restoration)
							log("STATE_READY: Emitting PROGRESS for restoration: position=$seekPos, duration=$dur")
							val buffered = engineBrowser?.bufferedPosition ?: 0L
							emitProgress(seekPos, dur, buffered, "restoration(pendingSeek)")
							
							// Note: flowPendingSeekPosition is NOT cleared here.
							// It will be cleared by onPositionDiscontinuity when the seek completes.
						}

						if (isActuallyPlaying) {
							// Defer emission to onEvents() for batching
							setPendingState(
								AudioProModule.STATE_PLAYING,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_READY, isPlaying=true)"
							)
						} else if (isSeeking && isPlayIntended) {
							// During a seek, STATE_READY may fire while isPlaying is
							// still false (Media3 hasn't resumed yet). Don't emit
							// PAUSED — onIsPlayingChanged(true) will follow shortly.
							log("STATE_READY(isPlaying=false) suppressed — seek in progress with playWhenReady=true")
						} else {
							// Defer emission to onEvents() for batching
							setPendingState(
								AudioProModule.STATE_PAUSED,
								pos,
								dur,
								"onPlaybackStateChanged(STATE_READY, isPlaying=false)"
							)
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
						// Immediate: stop progress timer
						stopProgressTimer()

						// Immediate: reset error state and last emitted state
						flowIsInErrorState = false
						flowLastEmittedState = ""
						flowLastEmittedPosition = null
						flowLastEmittedDuration = null

						// Snapshot duration before any player mutations
						val endedDur = safeDuration()

						// Check if repeat mode is enabled - if so, let Media3 handle it
						val repeatMode = engineBrowser?.repeatMode ?: Player.REPEAT_MODE_OFF
						if (repeatMode != Player.REPEAT_MODE_OFF) {
							// Repeat is enabled - Media3 will automatically restart playback
							// Don't pause or seek, just emit the track ended event (deferred)
							log("STATE_ENDED with repeat mode $repeatMode - letting Media3 handle repeat")
							pendingTrackEnded = true
							pendingTrackEndedDuration = endedDur
							return
						}

						// No repeat mode - pause and reset to beginning (immediate actions)
						// 1. Pause to stop playWhenReady before seeking
						engineBrowser?.pause()

						// 2. Seek to position 0
						engineBrowser?.seekTo(0)

						// 3. Cancel any pending seek operations
						flowPendingSeekPosition = null

						// 4. Queue STOPPED state for emission in onEvents()
						setPendingState(
							AudioProModule.STATE_STOPPED,
							0L,
							endedDur,
							"onPlaybackStateChanged(STATE_ENDED)"
						)

						// 5. Queue TRACK_ENDED for emission in onEvents()
						pendingTrackEnded = true
						pendingTrackEndedDuration = endedDur
					}

					Player.STATE_IDLE -> {
						// Immediate: stop progress timer
						stopProgressTimer()
						// Defer emission to onEvents() for batching
						setPendingState(
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
					log("onPositionDiscontinuity: oldPos=${oldPosition.positionMs}, newPos=${newPosition.positionMs}, reason=$reason, isRestoration=$flowIsRestorationSeek, pendingSeek=$flowPendingSeekPosition")
					
					// During restoration, the seekToDefaultPosition(index) fires a discontinuity
					// BEFORE STATE_READY. Don't consume the pending seek here — let STATE_READY handle it.
					if (flowIsRestorationSeek) {
						log("onPositionDiscontinuity: Skipping — restoration seek in progress, STATE_READY will handle it")
						return
					}

					val dur = safeDuration()

					val triggeredBy = if (flowPendingSeekPosition != null) {
						AudioProModule.TRIGGER_SOURCE_USER
					} else {
						AudioProModule.TRIGGER_SOURCE_SYSTEM
					}

					// Use the actual new position from the discontinuity event
					val pos = newPosition.positionMs
					
					// Clear the pending seek position BEFORE emitting events to prevent race conditions
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
			 * Called when the current media item changes. Updates activeTrack and
			 * emits TRACK_CHANGED so the JS layer can reflect the new track metadata.
			 */
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				log("onMediaItemTransition", "mediaId=", mediaItem?.mediaId, "reason=", reason, "duration=", engineBrowser?.duration)
				
				if (mediaItem != null) {
					val extras = mediaItem.mediaMetadata.extras
					if (extras != null) {
						val index = engineBrowser?.currentMediaItemIndex ?: -1
						
						val trackMap = Arguments.fromBundle(extras)
						// Add index to track map for convenience
						trackMap?.let {
							(it as? com.facebook.react.bridge.WritableMap)?.putInt("index", index)
						}
						
						activeTrack = trackMap // Update active track
						
						log("onMediaItemTransition: Updated activeTrack, title=${trackMap?.getString("title")}")

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

			/**
			 * Handles player errors with Media3-aligned classification:
			 * - Recoverable errors: Network timeouts, connection failures → emit error but don't tear down
			 * - Unrecoverable errors: Decoding failures, audio init failures → full teardown
			 *
			 * Media3 error codes are mapped to JS-friendly codes.
			 */
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
				
				val cause = error.cause?.let { cause ->
					errorDetails.append(" | Cause: ${cause.javaClass.simpleName}: ${cause.message}")
					"${cause.javaClass.simpleName}: ${cause.message}"
				}
				
				android.util.Log.e(Constants.LOG_TAG, errorDetails.toString(), error)

				val message = error.message ?: "Unknown error"
				val jsErrorCode = mapMedia3ErrorCode(error.errorCode)
				val recoverable = isRecoverableError(error.errorCode)

				// Emit PLAYBACK_ERROR event with enhanced details
				emitError(message, jsErrorCode, recoverable, cause, "onPlayerError(${error.errorCode})")

				if (!recoverable) {
					// Unrecoverable: use the shared resetInternal function to:
					// 1. Clear the player state (like clear())
					// 2. Emit STATE_CHANGED: ERROR
					resetInternal(AudioProModule.STATE_ERROR)
				} else {
					// Recoverable: Media3 will handle retry with exponential backoff
					// Don't tear down, just emit the error event
					log("Recoverable error, not tearing down player")
				}
			}

			/**
			 * Called after all events in a batch have been processed.
			 * This is where we emit batched state updates to JS.
			 * 
			 * Benefits of onEvents() pattern:
			 * 1. Single emission after all state changes settle
			 * 2. No UI flicker from rapid state transitions
			 * 3. Guaranteed consistency (e.g., STATE_READY + isPlaying both processed)
			 */
			override fun onEvents(player: Player, events: Player.Events) {
				log("onEvents: ${events.size()} events")
				emitBatchedEvents(player)
			}
		}

		engineBrowser?.addListener(enginePlayerListener!!)
	}

	/**
	 * Determines if an error is recoverable (network issues) or unrecoverable (codec/hardware).
	 * HTTP errors (4xx, 5xx) are considered recoverable since JS can refresh expired URLs.
	 */
	private fun isRecoverableError(errorCode: Int): Boolean {
		return when (errorCode) {
			// Network errors - recoverable (Media3 will retry)
			PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
			PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
			PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
			PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
			PlaybackException.ERROR_CODE_TIMEOUT,
			PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
			// HTTP errors (403/401 for expired URLs) - recoverable via URL refresh
			PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
			
			// Unrecoverable errors - require user intervention or different content
			else -> false
		}
	}

	/**
	 * Maps Media3 error codes to JS-friendly codes.
	 * Based on PlaybackException error code ranges.
	 */
	private fun mapMedia3ErrorCode(errorCode: Int): Int {
		return when (errorCode) {
			// Network errors (1xxx)
			PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> 1001
			PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 1002
			PlaybackException.ERROR_CODE_TIMEOUT -> 1003
			PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> 1004
			PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> 1005
			
			// Decoding errors (2xxx)
			PlaybackException.ERROR_CODE_DECODING_FAILED -> 2001
			PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> 2002
			PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 2003
			PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> 2004
			PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> 2005
			PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> 2006
			
			// DRM errors (3xxx)
			PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> 3001
			PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> 3002
			PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> 3003
			PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> 3004
			PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> 3005
			
			// Content errors (4xxx)
			PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> 4001
			PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> 4002
			PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> 4003
			PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> 4004
			PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> 4005
			
			// Unknown/unspecified
			PlaybackException.ERROR_CODE_UNSPECIFIED -> 0
			else -> errorCode // Pass through unknown codes
		}
	}

	private fun startProgressTimer() {
		stopProgressTimer()
		engineProgressHandler = Handler(Looper.getMainLooper())
		engineProgressRunnable = object : Runnable {
			override fun run() {
				// Use pending seek position if available to prevent UI jumping back
				val pos = flowPendingSeekPosition ?: (engineBrowser?.currentPosition ?: 0L)
				val dur = safeDuration()
				val buffered = engineBrowser?.bufferedPosition ?: 0L
				emitProgress(pos, dur, buffered, "progressTimer")
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

		val index = engineBrowser?.currentMediaItemIndex ?: -1
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
	 * Emits a PROGRESS event with position, duration, and bufferedPosition.
	 * The bufferedPosition indicates how much of the track has been buffered.
	 */
	private fun emitProgress(position: Long, duration: Long, bufferedPosition: Long, reason: String = "") {
		val sanitizedPosition = if (position < 0) 0L else position
		val sanitizedDuration = if (duration < 0) 0L else duration
		val sanitizedBuffered = if (bufferedPosition < 0) 0L else bufferedPosition

		val payload = Arguments.createMap().apply {
			putDouble("position", sanitizedPosition.toDouble())
			putDouble("duration", sanitizedDuration.toDouble())
			putDouble("bufferedPosition", sanitizedBuffered.toDouble())
		}
		emitEvent(AudioProModule.EVENT_TYPE_PROGRESS, activeTrack, payload, reason)
	}

	/**
	 * Emits a PLAYBACK_ERROR event to JavaScript with enhanced error details.
	 *
	 * According to the contract in logic.md:
	 * - PLAYBACK_ERROR and ERROR state are separate and must not be conflated
	 * - PLAYBACK_ERROR can be emitted with or without a corresponding state change
	 * - Useful for soft errors (e.g., image fetch failed, headers issue, non-fatal network retry)
	 *
	 * @param message Human-readable error message
	 * @param code JS-friendly error code (mapped from Media3 error codes)
	 * @param recoverable Whether the error is recoverable (true = player still usable)
	 * @param cause Optional cause string from the underlying exception
	 * @param reason Debug reason for logging
	 */
	private fun emitError(message: String, code: Int, recoverable: Boolean = false, cause: String? = null, reason: String = "") {
		val index = engineBrowser?.currentMediaItemIndex ?: -1
		val payload = Arguments.createMap().apply {
			putString("error", message)
			putInt("errorCode", code)
			putBoolean("recoverable", recoverable)
			putInt("index", index)
			cause?.let { putString("cause", it) }
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
			engineBrowser?.setPlaybackSpeed(speed)

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
			engineBrowser?.setVolume(volume)
		}
	}


	suspend fun seekBy(offsetMs: Long) {
		ensureSession()
		runOnUiThread {
			val current = engineBrowser?.currentPosition ?: 0L
			val duration = safeDuration()
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
