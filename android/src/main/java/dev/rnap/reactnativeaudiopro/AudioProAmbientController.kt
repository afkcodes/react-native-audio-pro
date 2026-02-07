package dev.rnap.reactnativeaudiopro

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * AudioProAmbientController
 *
 * A completely isolated controller for ambient audio playback.
 * This controller is separate from the main AudioProController and does not
 * share any state, events, or resources with it.
 */
object AudioProAmbientController {
	private const val TAG = Constants.LOG_TAG
	private const val AMBIENT_EVENT_NAME = "AudioProAmbientEvent"
	private const val EVENT_TYPE_AMBIENT_TRACK_ENDED = "AMBIENT_TRACK_ENDED"
	private const val EVENT_TYPE_AMBIENT_ERROR = "AMBIENT_ERROR"

	private var reactContext: ReactApplicationContext? = null
	
	// MediaBrowser instead of ExoPlayer directly
	private lateinit var browserFuture: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaBrowser>
	private var browser: androidx.media3.session.MediaBrowser? = null
	private var isConnecting: Boolean = false

	private var settingDebugAmbient: Boolean = false
	private var settingLoopAmbient: Boolean = true
	private var settingVolumeAmbient: Float = 1.0f
	
	private val playerListener = object : Player.Listener {
		override fun onPlaybackStateChanged(state: Int) {
			if (state == Player.STATE_ENDED) {
				if (!settingLoopAmbient) {
					emitAmbientTrackEnded()
					ambientStop()
				} else {
					// Loop is handled by RepeatMode, but if we wanted manual looping...
					// RepeatMode is set in ambientPlay
				}
			}
		}

		override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
			emitAmbientError(error.message ?: "Unknown ambient playback error")
			// We might not want to stop completely on error, but for now matching old behavior
			ambientStop()
		}
	}

	fun setReactContext(context: ReactApplicationContext?) {
		reactContext = context
	}

	private fun log(vararg args: Any?) {
		if (settingDebugAmbient) {
			Log.d(TAG, "${args.joinToString(" ")}")
		}
	}
	
	private fun ensureSession() {
		if (!isConnecting && (!::browserFuture.isInitialized || browser?.isConnected != true)) {
			connectToService()
		}
	}
	
	private fun connectToService() {
		val context = reactContext ?: return
		isConnecting = true
		
		val connectionHints = android.os.Bundle()
		connectionHints.putString("type", "ambient")
		
		val token = androidx.media3.session.SessionToken(
			context,
			android.content.ComponentName(context, AudioProPlaybackService::class.java)
		)
		
		browserFuture = androidx.media3.session.MediaBrowser.Builder(context, token)
			.setConnectionHints(connectionHints)
			.buildAsync()
			
		browserFuture.addListener({
			try {
				browser = browserFuture.get()
				browser?.addListener(playerListener)
				log("Connected to Ambient Session")
				
				// Apply pending settings if any?
				browser?.volume = settingVolumeAmbient
			} catch (e: Exception) {
				Log.e(TAG, "Failed to connect to Ambient Session", e)
			} finally {
				isConnecting = false
			}
		}, androidx.core.content.ContextCompat.getMainExecutor(context))
	}

	fun ambientPlay(options: ReadableMap) {
		val optionUrl = options.getString("url") ?: run {
			emitAmbientError("Invalid URL provided to ambientPlay()")
			return
		}

		if (options.hasKey("debug")) settingDebugAmbient = options.getBoolean("debug")

		val optionLoop = if (options.hasKey("loop")) options.getBoolean("loop") else true
		settingLoopAmbient = optionLoop

		log("Ambient options parsed: url=$optionUrl, loop=$optionLoop")

		// Ensure we are connected
		val context = reactContext ?: return
		if (browser?.isConnected != true) {
			// If not connected, we need to connect and THEN play.
			// This async nature might lose the first play command if not handled carefully.
			// For simplicity in this refactor, we attempt connection and retry play?
			// Or we just block/wait? We can't block UI thread.
			// We'll require a valid session.
			connectToService()
			// We can't easily queue the command without a queue system.
			// BUT since this is a refactor, let's try to be robust.
			// Ideally existing implementation was synchronous-ish (ExoPlayer builder on UI thread).
			// Here we are async.
			// Let's add a one-off runnable to run after connection?
			browserFuture.addListener({
				// Re-run logic on main thread
				Handler(Looper.getMainLooper()).post {
					executePlay(optionUrl, optionLoop)
				}
			}, androidx.core.content.ContextCompat.getMainExecutor(context))
			return
		}
		
		executePlay(optionUrl, optionLoop)
	}
	
	private fun executePlay(url: String, loop: Boolean) {
		browser?.let { player ->
			val uri = android.net.Uri.parse(url)
			val mediaItem = MediaItem.Builder().setUri(uri).build()
			
			player.setMediaItem(mediaItem)
			player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
			player.volume = settingVolumeAmbient
			player.prepare()
			player.play()
		}
	}

	fun ambientStop() {
		log("Ambient Stop")
		browser?.stop()
		browser?.clearMediaItems()
		// We verify release in AudioProPlaybackService.onTaskRemoved, 
		// but here we just stop playback.
		// We do NOT release the browser typically unless module is destroyed.
	}

	fun ambientPause() {
		log("Ambient Pause")
		browser?.pause()
	}

	fun ambientResume() {
		log("Ambient Resume")
		browser?.play()
	}

	fun ambientSeekTo(positionMs: Long) {
		log("Ambient Seek To", positionMs)
		browser?.seekTo(positionMs)
	}

	fun ambientSetVolume(volume: Float) {
		settingVolumeAmbient = volume
		log("Ambient Set Volume", volume)
		browser?.volume = volume
	}

	private fun emitAmbientTrackEnded() {
		log("Ambient Track Ended")
		emitAmbientEvent(EVENT_TYPE_AMBIENT_TRACK_ENDED, null)
	}

	private fun emitAmbientError(message: String) {
		log("Ambient Error:", message)
		val payload = Arguments.createMap().apply {
			putString("error", message)
		}
		emitAmbientEvent(EVENT_TYPE_AMBIENT_ERROR, payload)
	}

	private fun emitAmbientEvent(type: String, payload: WritableMap?) {
		val context = reactContext
		if (context is ReactApplicationContext) {
			val body = Arguments.createMap().apply {
				putString("type", type)
				if (payload != null) {
					putMap("payload", payload)
				}
			}
			context
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
				.emit(AMBIENT_EVENT_NAME, body)
		} else {
			Log.w(TAG, "Context is not an instance of ReactApplicationContext")
		}
	}
	
	private fun runOnUiThread(block: () -> Unit) {
		Handler(Looper.getMainLooper()).post(block)
	}
}
