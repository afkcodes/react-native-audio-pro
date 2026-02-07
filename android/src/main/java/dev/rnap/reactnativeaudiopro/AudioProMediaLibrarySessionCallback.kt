package dev.rnap.reactnativeaudiopro

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
open class AudioProMediaLibrarySessionCallback(private val service: AudioProPlaybackService) : MediaLibraryService.MediaLibrarySession.Callback {

	private val nextButton = CommandButton.Builder(CommandButton.ICON_NEXT)
		.setDisplayName("Next")
		.setSessionCommand(
			SessionCommand(
				Constants.CUSTOM_COMMAND_NEXT,
				Bundle.EMPTY
			)
		)
		.build()

	private val prevButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
		.setDisplayName("Previous")
		.setSessionCommand(
			SessionCommand(
				Constants.CUSTOM_COMMAND_PREV,
				Bundle.EMPTY
			)
		)
		.build()

	private val skipForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
		.setDisplayName("Skip Forward")
		.setSessionCommand(
			SessionCommand(
				Constants.CUSTOM_COMMAND_SKIP_FORWARD,
				Bundle.EMPTY
			)
		)
		.build()

	private val skipBackwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
		.setDisplayName("Skip Backward")
		.setSessionCommand(
			SessionCommand(
				Constants.CUSTOM_COMMAND_SKIP_BACKWARD,
				Bundle.EMPTY
			)
		)
		.build()

	private fun getCommandButtons(): List<CommandButton> {
		val buttons = mutableListOf<CommandButton>()

		// Always provide 15-/30-second skip controls
		buttons.add(skipBackwardButton)
		buttons.add(skipForwardButton)

		if (AudioProController.settingShowNextPrevControls) {
			AudioProController.log("Next/Prev controls are enabled")
			buttons.add(nextButton)
			buttons.add(prevButton)
		} else {
			AudioProController.log("Next/Prev controls are disabled")
		}

		return buttons
	}

	@OptIn(UnstableApi::class) // MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
	val mediaNotificationSessionCommands
		get() = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
			.also { builder ->
				// Add custom commands based on settings
				if (AudioProController.settingShowNextPrevControls) {
					// Add next and previous commands
					builder.add(SessionCommand(Constants.CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
					builder.add(SessionCommand(Constants.CUSTOM_COMMAND_PREV, Bundle.EMPTY))
				} else if (AudioProController.settingShowSkipControls) {
					// Add skip forward and skip backward commands
					builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
					builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SKIP_BACKWARD, Bundle.EMPTY))
				}
				
				// Add Ambient Commands
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_PLAY, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_STOP, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_PAUSE, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_RESUME, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_SEEK, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_AMBIENT_SET_VOLUME, Bundle.EMPTY))

				// Add DSP Commands
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SET_EQUALIZER, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SET_BASS_BOOST, Bundle.EMPTY))
			}
			.build()

	@OptIn(UnstableApi::class)
	override fun onConnect(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
	): MediaSession.ConnectionResult {
		return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
			.setAvailableSessionCommands(mediaNotificationSessionCommands)
			.setMediaButtonPreferences(getCommandButtons())
			.build()
	}

	@OptIn(UnstableApi::class) // MediaSession.isMediaNotificationController
	override fun onCustomCommand(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
		customCommand: SessionCommand,
		args: Bundle,
	): ListenableFuture<SessionResult> {
		AudioProController.log("onCustomCommand: ${customCommand.customAction}")
		when (customCommand.customAction) {
			Constants.CUSTOM_COMMAND_NEXT -> {
				AudioProController.emitNext()
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_PREV -> {
				AudioProController.emitPrev()
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SKIP_FORWARD -> {
				CoroutineScope(Dispatchers.Main).launch {
					AudioProController.seekForward(AudioProController.settingSkipIntervalMs)
				}
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SKIP_BACKWARD -> {
				CoroutineScope(Dispatchers.Main).launch {
					AudioProController.seekBack(AudioProController.settingSkipIntervalMs)
				}
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}
			
			Constants.CUSTOM_COMMAND_AMBIENT_PLAY -> {
				val url = args.getString("url") ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
				// We also need other options passed in args if available
				service.handleAmbientPlay(args)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}
			
			Constants.CUSTOM_COMMAND_AMBIENT_STOP -> {
				service.handleAmbientStop()
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_AMBIENT_PAUSE -> {
				service.handleAmbientPause()
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_AMBIENT_RESUME -> {
				service.handleAmbientResume()
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_AMBIENT_SEEK -> {
				val pos = args.getLong("position", 0L)
				service.handleAmbientSeek(pos)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_AMBIENT_SET_VOLUME -> {
				val vol = args.getFloat("volume", 1.0f)
				service.handleAmbientSetVolume(vol)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SET_EQUALIZER -> {
				val gains = args.getFloatArray("gains")
				if (gains != null) {
					service.handleSetEqualizer(gains)
				}
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SET_BASS_BOOST -> {
				val strength = args.getInt("strength", 0)
				service.handleSetBassBoost(strength)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}
		}

		return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
	}

	override fun onAddMediaItems(
		mediaSession: MediaSession,
		controller: MediaSession.ControllerInfo,
		mediaItems: List<MediaItem>,
	): ListenableFuture<List<MediaItem>> {
		return Futures.immediateFuture(mediaItems)
	}

}
