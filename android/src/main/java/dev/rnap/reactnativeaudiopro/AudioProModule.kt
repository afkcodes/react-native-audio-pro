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
		const val EVENT_TYPE_REPEAT_MODE_CHANGED = "REPEAT_MODE_CHANGED"
		const val EVENT_TYPE_SHUFFLE_MODE_CHANGED = "SHUFFLE_MODE_CHANGED"
		const val EVENT_TYPE_CUSTOM_ACTION = "CUSTOM_ACTION"
		const val EVENT_TYPE_SLEEP_TIMER_COMPLETE = "SLEEP_TIMER_COMPLETE"
		const val EVENT_TYPE_QUEUE_CHANGED = "QUEUE_CHANGED"
		const val EVENT_TYPE_AUDIO_SESSION_CHANGED = "AUDIO_SESSION_CHANGED"

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
	fun configure(options: ReadableMap) {
		AudioProController.configure(options)
	}

	@ReactMethod
	fun syncFromNative() {
		AudioProController.syncFromNative()
	}

	@ReactMethod
	fun addMediaItems(tracks: com.facebook.react.bridge.ReadableArray) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.addMediaItems(tracks)
		}
	}

	@ReactMethod
	fun clearMediaItems() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.clearMediaItems()
		}
	}

	/**
	 * Insert media items at a specific position in the queue.
	 */
	@ReactMethod
	fun addMediaItemsAt(index: Double, tracks: com.facebook.react.bridge.ReadableArray) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.addMediaItemsAt(index.toInt(), tracks)
		}
	}

	/**
	 * Remove a range of media items from the queue.
	 * @param fromIndex Start index (inclusive)
	 * @param toIndex End index (exclusive)
	 */
	@ReactMethod
	fun removeMediaItems(fromIndex: Double, toIndex: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.removeMediaItems(fromIndex.toInt(), toIndex.toInt())
		}
	}

	/**
	 * Move a media item from one position to another in the queue.
	 */
	@ReactMethod
	fun moveMediaItem(fromIndex: Double, toIndex: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.moveMediaItem(fromIndex.toInt(), toIndex.toInt())
		}
	}

	/**
	 * Set media items (replaces entire queue).
	 */
	@ReactMethod
	fun setMediaItems(tracks: com.facebook.react.bridge.ReadableArray) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setMediaItems(tracks)
		}
	}

	@ReactMethod
	fun seekToMediaItem(index: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekToMediaItem(index.toInt())
		}
	}

	@ReactMethod
	fun seekToMediaItemWithPosition(index: Double, position: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekToMediaItemWithPosition(index.toInt(), position.toLong())
		}
	}

	@ReactMethod
	fun removeMediaItem(index: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.removeMediaItem(index.toInt())
		}
	}




	
	@ReactMethod
	fun getMediaItems(promise: com.facebook.react.bridge.Promise) {
		CoroutineScope(Dispatchers.Main).launch {
			try {
				val queue = AudioProController.getMediaItems()
				promise.resolve(queue)
			} catch (e: Exception) {
				promise.reject("GET_MEDIA_ITEMS_ERROR", e)
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
	fun seekToNextMediaItem() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekToNextMediaItem()
		}
	}
	
	@ReactMethod
	fun seekToPreviousMediaItem() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.seekToPreviousMediaItem()
		}
	}

	@ReactMethod
	fun setRepeatMode(mode: String) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setRepeatMode(mode)
		}
	}
	
	@ReactMethod
	fun setShuffleModeEnabled(enabled: Boolean) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setShuffleModeEnabled(enabled)
		}
	}

	@ReactMethod
	fun setNotificationButtons(buttons: com.facebook.react.bridge.ReadableArray) {
		AudioProController.setNotificationButtons(buttons)
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
	fun setEqualizer(gains: com.facebook.react.bridge.ReadableArray) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setEqualizer(gains)
		}
	}

	@ReactMethod
	fun setBassBoost(strength: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setBassBoost(strength.toInt())
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

	@ReactMethod
	fun startSleepTimer(seconds: Double) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.startSleepTimer(seconds)
		}
	}

	@ReactMethod
	fun cancelSleepTimer() {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.cancelSleepTimer()
		}
	}

	@ReactMethod
	fun setSkipSilence(enabled: Boolean) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.setSkipSilence(enabled)
		}
	}

	/**
	 * Updates the notification button states (like/dislike/bookmark).
	 * Call this when track state changes to update the notification icons.
	 */
	@ReactMethod
	fun updateNotificationState(liked: Boolean, disliked: Boolean, bookmarked: Boolean) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.updateNotificationState(liked, disliked, bookmarked)
		}
	}

	@ReactMethod
	fun updateTrack(index: Double, track: ReadableMap) {
		CoroutineScope(Dispatchers.Main).launch {
			AudioProController.updateTrack(index.toInt(), track)
		}
	}

	@ReactMethod
	fun getCacheSize(promise: com.facebook.react.bridge.Promise) {
		try {
			// Using Dispatchers.IO for file operations
			CoroutineScope(Dispatchers.IO).launch {
				try {
					val size = AudioProCache.getCacheSize(reactApplicationContext)
					promise.resolve(size.toDouble())
				} catch (e: Exception) {
					promise.reject("CACHE_ERROR", "Failed to get cache size", e)
				}
			}
		} catch (e: Exception) {
			promise.reject("CACHE_ERROR", "Failed to launch coroutine", e)
		}
	}

	@ReactMethod
	fun clearCache(promise: com.facebook.react.bridge.Promise) {
		try {
			CoroutineScope(Dispatchers.IO).launch {
				try {
					AudioProCache.clearCache(reactApplicationContext)
					promise.resolve(true)
				} catch (e: Exception) {
					promise.reject("CACHE_ERROR", "Failed to clear cache", e)
				}
			}
		} catch (e: Exception) {
			promise.reject("CACHE_ERROR", "Failed to launch coroutine", e)
		}
	}

	override fun onHostResume() {}

	override fun onHostPause() {}
}
