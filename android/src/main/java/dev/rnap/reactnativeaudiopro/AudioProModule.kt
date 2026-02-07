package dev.rnap.reactnativeaudiopro

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.LifecycleEventListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioProModule(private val reactContext: ReactApplicationContext) :
	ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

	companion object {
		const val NAME = "AudioPro"

		const val EVENT_NAME = "AudioProEvent"

		const val STATE_IDLE = "IDLE"
		const val STATE_PLAYING = "PLAYING"
		const val STATE_PAUSED = "PAUSED"
		const val STATE_STOPPED = "STOPPED"
		const val STATE_LOADING = "LOADING"
		const val STATE_ERROR = "ERROR"

		const val EVENT_TYPE_STATE_CHANGED = "STATE_CHANGED"
		const val EVENT_TYPE_TRACK_ENDED = "TRACK_ENDED"
		const val EVENT_TYPE_TRACK_CHANGED = "TRACK_CHANGED"
		const val EVENT_TYPE_PLAYBACK_ERROR = "PLAYBACK_ERROR"
		const val EVENT_TYPE_PROGRESS = "PROGRESS"
		const val EVENT_TYPE_SEEK_COMPLETE = "SEEK_COMPLETE"
		const val EVENT_TYPE_REMOTE_NEXT = "REMOTE_NEXT"
		const val EVENT_TYPE_REMOTE_PREV = "REMOTE_PREV"
		const val EVENT_TYPE_PLAYBACK_SPEED_CHANGED = "PLAYBACK_SPEED_CHANGED"

		// Trigger sources for seek events
		const val TRIGGER_SOURCE_USER = "USER"
		const val TRIGGER_SOURCE_SYSTEM = "SYSTEM"
	}

	init {
		AudioProController.setReactContext(reactContext)
		AudioProAmbientController.setReactContext(reactContext)
		reactContext.addLifecycleEventListener(this)
	}

	@ReactMethod
	fun play(track: ReadableMap?, options: ReadableMap?) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.play(track, options)
		}
	}

	@ReactMethod
	fun addToQueue(tracks: com.facebook.react.bridge.ReadableArray) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.addToQueue(tracks)
		}
	}

	@ReactMethod
	fun clearQueue() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.clearQueue()
		}
	}

	@ReactMethod
	fun skipTo(index: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.skipTo(index.toInt())
		}
	}

	@ReactMethod
	fun removeTrack(index: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.removeTrack(index.toInt())
		}
	}


	
	@ReactMethod
	fun getQueue(promise: com.facebook.react.bridge.Promise) {
		CoroutineScope(Dispatchers.Main).launch {
			try {
				val queue = AudioProController.getQueue()
				promise.resolve(queue)
			} catch (e: Exception) {
				promise.reject("GET_QUEUE_ERROR", e)
			}
		}
	}

	@ReactMethod
	fun pause() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.pause()
		}
	}

	@ReactMethod
	fun resume() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.resume()
		}
	}

	@ReactMethod
	fun stop() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.stop()
		}
	}

	@ReactMethod
	fun seekTo(position: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekTo(position.toLong())
		}
	}

	@ReactMethod
	fun seekBy(offset: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekBy(offset.toLong())
		}
	}

	@ReactMethod
	fun playNext() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.playNext()
		}
	}
	
	@ReactMethod
	fun playPrevious() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.playPrevious()
		}
	}

	@ReactMethod
	fun setRepeatMode(mode: String) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setRepeatMode(mode)
		}
	}
	
	@ReactMethod
	fun setShuffleMode(enabled: Boolean) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setShuffleMode(enabled)
		}
	}

	@ReactMethod
	fun setPlaybackSpeed(speed: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setPlaybackSpeed(speed.toFloat())
		}
	}

	@ReactMethod
	fun setVolume(volume: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setVolume(volume.toFloat())
		}
	}

	@ReactMethod
	fun clear() {
		AudioProController.clear()
	}

	@ReactMethod
	fun ambientPlay(options: ReadableMap) {
		AudioProAmbientController.ambientPlay(options)
	}

	@ReactMethod
	fun ambientStop() {
		AudioProAmbientController.ambientStop()
	}

	@ReactMethod
	fun ambientSetVolume(volume: Double) {
		AudioProAmbientController.ambientSetVolume(volume.toFloat())
	}

	@ReactMethod
	fun ambientPause() {
		AudioProAmbientController.ambientPause()
	}

	@ReactMethod
	fun ambientResume() {
		AudioProAmbientController.ambientResume()
	}

	@ReactMethod
	fun ambientSeekTo(positionMs: Double) {
		AudioProAmbientController.ambientSeekTo(positionMs.toLong())
	}

    // Keep: Required for RN built in Event Emitter Calls.
    @ReactMethod fun addListener(eventName: String) {}

    @ReactMethod fun removeListeners(count: Int) {}

	override fun getName(): String {
		return NAME
	}

	override fun onHostDestroy() {
		if (!reactContext.hasActiveCatalystInstance()) {
			Log.d(Constants.LOG_TAG, "App is being destroyed, clearing playback")
			AudioProController.clear()
			AudioProAmbientController.ambientStop()
		}
	}

	override fun onCatalystInstanceDestroy() {
		Log.d("AudioProModule", "React Native bridge is being destroyed, clearing playback")
		AudioProController.clear()
		AudioProAmbientController.ambientStop()

		// Explicitly null out context references
		AudioProController.setReactContext(null)
		AudioProAmbientController.setReactContext(null)

		try {
			reactContext.removeLifecycleEventListener(this)
		} catch (e: Exception) {
			Log.e("AudioProModule", "Error removing lifecycle listener", e)
		}
		super.onCatalystInstanceDestroy()
	}

	override fun onHostResume() {}

	override fun onHostPause() {}
}
