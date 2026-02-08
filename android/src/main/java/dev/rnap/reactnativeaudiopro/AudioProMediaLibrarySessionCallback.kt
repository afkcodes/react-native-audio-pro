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

	/**
	 * Creates CommandButtons dynamically based on the notification button configuration
	 */
	private fun getCommandButtons(): List<CommandButton> {
		val buttons = mutableListOf<CommandButton>()
		val buttonConfig = AudioProController.settingNotificationButtons

		AudioProController.log("Building command buttons from config: $buttonConfig")

		for ((index, buttonType) in buttonConfig.withIndex()) {
			val button = when (buttonType) {
				"PREV" -> CommandButton.Builder(CommandButton.ICON_PREVIOUS)
					.setDisplayName("Previous")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_PREV, Bundle.EMPTY))
					.build()

				"NEXT" -> CommandButton.Builder(CommandButton.ICON_NEXT)
					.setDisplayName("Next")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
					.build()

				"LIKE" -> CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
					.setDisplayName("Like")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
					.build()

				"DISLIKE" -> CommandButton.Builder(CommandButton.ICON_THUMB_DOWN_UNFILLED)
					.setDisplayName("Dislike")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_DISLIKE, Bundle.EMPTY))
					.build()

				"SAVE" -> CommandButton.Builder(CommandButton.ICON_PLAYLIST_ADD)
					.setDisplayName("Save")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_SAVE, Bundle.EMPTY))
					.build()

				"BOOKMARK" -> CommandButton.Builder(CommandButton.ICON_BOOKMARK_UNFILLED)
					.setDisplayName("Bookmark")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_BOOKMARK, Bundle.EMPTY))
					.build()

				"REWIND_30" -> CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
					.setDisplayName("Rewind 30s")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_REWIND_30, Bundle.EMPTY))
					.build()

				"FORWARD_30" -> CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
					.setDisplayName("Forward 30s")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_FORWARD_30, Bundle.EMPTY))
					.build()

				else -> {
					AudioProController.log("Unknown button type: $buttonType, skipping")
					null
				}
			}

			button?.let { buttons.add(it) }
		}

		AudioProController.log("Created ${buttons.size} command buttons")
		return buttons
	}

	@OptIn(UnstableApi::class) // MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
	val mediaNotificationSessionCommands
		get() = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
			.also { builder ->
				// Add custom commands based on button configuration
				val buttonConfig = AudioProController.settingNotificationButtons
				
				for (buttonType in buttonConfig) {
					when (buttonType) {
						"PREV" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_PREV, Bundle.EMPTY))
						"NEXT" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
						"LIKE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
						"DISLIKE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_DISLIKE, Bundle.EMPTY))
						"SAVE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SAVE, Bundle.EMPTY))
						"BOOKMARK" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_BOOKMARK, Bundle.EMPTY))
						"REWIND_30" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_REWIND_30, Bundle.EMPTY))
						"FORWARD_30" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_FORWARD_30, Bundle.EMPTY))
					}
				}
				
				// Always add skip forward/backward commands
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SKIP_BACKWARD, Bundle.EMPTY))
				
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

				// Add Playlist Commands
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SET_REPEAT_MODE, Bundle.EMPTY))
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SET_SHUFFLE_MODE, Bundle.EMPTY))
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

			// Custom notification actions
			Constants.CUSTOM_COMMAND_LIKE -> {
				AudioProController.emitCustomAction("LIKE")
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_DISLIKE -> {
				AudioProController.emitCustomAction("DISLIKE")
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SAVE -> {
				AudioProController.emitCustomAction("SAVE")
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_BOOKMARK -> {
				AudioProController.emitCustomAction("BOOKMARK")
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_REWIND_30 -> {
				CoroutineScope(Dispatchers.Main).launch {
					AudioProController.seekBack(30000L)
				}
				// Also emit as custom action so JS can respond if needed
				AudioProController.emitCustomAction("REWIND_30")
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_FORWARD_30 -> {
				CoroutineScope(Dispatchers.Main).launch {
					AudioProController.seekForward(30000L)
				}
				// Also emit as custom action so JS can respond if needed
				AudioProController.emitCustomAction("FORWARD_30")
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

			Constants.CUSTOM_COMMAND_SET_REPEAT_MODE -> {
				val mode = args.getString("mode", "OFF")
				service.handleSetRepeatMode(mode)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SET_SHUFFLE_MODE -> {
				val enabled = args.getBoolean("enabled", false)
				service.handleSetShuffleMode(enabled)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_SET_SKIP_SILENCE -> {
				val enabled = args.getBoolean("enabled", false)
				service.handleSetSkipSilence(enabled)
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
