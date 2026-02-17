package dev.rnap.reactnativeaudiopro

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener

/**
 * Manages Google Cast integration for react-native-audio-pro.
 *
 * Media3 1.9.0's CastPlayer.Builder with setLocalPlayer() handles the
 * local↔cast player switching automatically. This manager:
 * - Initializes CastContext with the consuming app's receiver ID
 * - Creates a CastPlayer that wraps the ExoPlayer
 * - Tracks cast connection state and exposes it to RN
 * - Emits CAST_STATE_CHANGED events through the controller
 *
 * The consuming app must:
 * 1. Call AudioPro.initializeCast(receiverAppId) early in the app lifecycle
 * 2. Provide their own CastOptionsProvider in AndroidManifest.xml
 * 3. Add a MediaRouteButton to their UI for device selection
 */
@OptIn(UnstableApi::class)
object AudioProCastManager {

	private var castContext: CastContext? = null
	private var castPlayer: CastPlayer? = null
	private var castStateListener: CastStateListener? = null
	private var isInitialized = false
	private var currentCastState: Int = CastState.NO_DEVICES_AVAILABLE

	/**
	 * Whether casting is currently active (connected to a remote device).
	 */
	val isCasting: Boolean
		get() = currentCastState == CastState.CONNECTED

	/**
	 * Whether cast devices are available for connection.
	 */
	val isCastAvailable: Boolean
		get() = currentCastState != CastState.NO_DEVICES_AVAILABLE

	/**
	 * Initialize Cast with the app's CastContext.
	 *
	 * The consuming app must have a CastOptionsProvider configured in their
	 * AndroidManifest.xml that returns their Cast receiver app ID.
	 *
	 * @param context Application context
	 * @param localPlayer The ExoPlayer instance to wrap with CastPlayer
	 * @return The CastPlayer if initialization succeeds, null otherwise
	 */
	fun initialize(context: Context, localPlayer: ExoPlayer): CastPlayer? {
		if (isInitialized && castPlayer != null) {
			Log.d(Constants.LOG_TAG, "CastManager: Already initialized")
			return castPlayer
		}

		return try {
			// CastContext.getSharedInstance() requires a CastOptionsProvider in the
			// consuming app's manifest. If not configured, this throws.
			castContext = CastContext.getSharedInstance(context)

			// Build CastPlayer with local player — Media3 1.9.0 handles switching
			castPlayer = CastPlayer.Builder(castContext!!)
				.setLocalPlayer(localPlayer)
				.build()

			// Track cast state changes
			castStateListener = CastStateListener { state ->
				val previousState = currentCastState
				currentCastState = state

				Log.d(Constants.LOG_TAG, "CastManager: State changed: ${castStateToString(previousState)} → ${castStateToString(state)}")

				// Emit state change event to RN
				AudioProController.emitCastStateChanged(
					castStateToString(state),
					state == CastState.CONNECTED
				)
			}
			castContext?.addCastStateListener(castStateListener!!)

			// Read initial state
			currentCastState = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE

			isInitialized = true
			Log.i(Constants.LOG_TAG, "CastManager: Initialized successfully, initial state: ${castStateToString(currentCastState)}")

			castPlayer
		} catch (e: Exception) {
			Log.e(Constants.LOG_TAG, "CastManager: Failed to initialize. Does the consuming app have a CastOptionsProvider?", e)
			null
		}
	}

	/**
	 * Get the CastPlayer instance, or null if not initialized.
	 */
	fun getCastPlayer(): CastPlayer? = castPlayer

	/**
	 * Clean up cast resources.
	 */
	fun release() {
		castStateListener?.let { listener ->
			castContext?.removeCastStateListener(listener)
		}
		castStateListener = null

		castPlayer?.release()
		castPlayer = null
		castContext = null
		isInitialized = false
		currentCastState = CastState.NO_DEVICES_AVAILABLE

		Log.d(Constants.LOG_TAG, "CastManager: Released")
	}

	private fun castStateToString(state: Int): String = when (state) {
		CastState.NO_DEVICES_AVAILABLE -> "NO_DEVICES_AVAILABLE"
		CastState.NOT_CONNECTED -> "NOT_CONNECTED"
		CastState.CONNECTING -> "CONNECTING"
		CastState.CONNECTED -> "CONNECTED"
		else -> "UNKNOWN"
	}
}
