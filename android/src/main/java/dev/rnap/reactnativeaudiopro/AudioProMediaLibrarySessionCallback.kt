package dev.rnap.reactnativeaudiopro

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
open class AudioProMediaLibrarySessionCallback(private val service: AudioProPlaybackService) : MediaLibraryService.MediaLibrarySession.Callback {

	// Reference to the session for dynamic button updates
	private var session: MediaSession? = null

	// Dynamic button states
	private var isLiked: Boolean = false
	private var isDisliked: Boolean = false
	private var isBookmarked: Boolean = false

	/**
	 * Updates the liked state and refreshes notification buttons.
	 * Called when user likes/unlikes the current track.
	 */
	fun updateLikedState(liked: Boolean) {
		isLiked = liked
		refreshMediaButtonPreferences()
	}

	/**
	 * Updates the disliked state and refreshes notification buttons.
	 */
	fun updateDislikedState(disliked: Boolean) {
		isDisliked = disliked
		refreshMediaButtonPreferences()
	}

	/**
	 * Updates the bookmarked state and refreshes notification buttons.
	 */
	fun updateBookmarkedState(bookmarked: Boolean) {
		isBookmarked = bookmarked
		refreshMediaButtonPreferences()
	}

	/**
	 * Updates all button states at once (e.g., when track changes).
	 */
	fun updateButtonStates(liked: Boolean, disliked: Boolean, bookmarked: Boolean) {
		isLiked = liked
		isDisliked = disliked
		isBookmarked = bookmarked
		refreshMediaButtonPreferences()
	}

	/**
	 * Refreshes the media button preferences on the session.
	 * This triggers a notification update with the new button states.
	 */
	private fun refreshMediaButtonPreferences() {
		session?.let { 
			it.setMediaButtonPreferences(getCommandButtons())
			AudioProController.log("Updated media button preferences - liked=$isLiked, disliked=$isDisliked, bookmarked=$isBookmarked")
		}
	}

	/**
	 * Creates CommandButtons dynamically based on the notification button configuration
	 * and current button states (liked, disliked, bookmarked).
	 */
	private fun getCommandButtons(): List<CommandButton> {
		val buttons = mutableListOf<CommandButton>()
		val buttonConfig = AudioProController.settingNotificationButtons

		AudioProController.log("Building command buttons from config: $buttonConfig")

		for ((index, buttonType) in buttonConfig.withIndex()) {
			val button = when (buttonType) {
				// Standard Media3 Player commands for navigation
				"PREV" -> CommandButton.Builder(CommandButton.ICON_PREVIOUS)
					.setDisplayName("Previous")
					.setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
					.build()

				"NEXT" -> CommandButton.Builder(CommandButton.ICON_NEXT)
					.setDisplayName("Next")
					.setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
					.build()

				// Dynamic state buttons - icon changes based on current state
				"LIKE" -> CommandButton.Builder(
						if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
					)
					.setDisplayName(if (isLiked) "Unlike" else "Like")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
					.build()

				"DISLIKE" -> CommandButton.Builder(
						if (isDisliked) CommandButton.ICON_THUMB_DOWN_FILLED else CommandButton.ICON_THUMB_DOWN_UNFILLED
					)
					.setDisplayName("Dislike")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_DISLIKE, Bundle.EMPTY))
					.build()

				"SAVE" -> CommandButton.Builder(CommandButton.ICON_PLAYLIST_ADD)
					.setDisplayName("Save")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_SAVE, Bundle.EMPTY))
					.build()

				"BOOKMARK" -> CommandButton.Builder(
						if (isBookmarked) CommandButton.ICON_BOOKMARK_FILLED else CommandButton.ICON_BOOKMARK_UNFILLED
					)
					.setDisplayName(if (isBookmarked) "Remove Bookmark" else "Bookmark")
					.setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_BOOKMARK, Bundle.EMPTY))
					.build()

				"REWIND_30" -> CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
					.setDisplayName("Rewind 30s")
					.setPlayerCommand(Player.COMMAND_SEEK_BACK)
					.build()

				"FORWARD_30" -> CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
					.setDisplayName("Forward 30s")
					.setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
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
				// Add custom session commands (app-specific features only)
				// PREV/NEXT use standard Player.COMMAND_SEEK_TO_* (not custom commands)
				val buttonConfig = AudioProController.settingNotificationButtons
				
				for (buttonType in buttonConfig) {
					when (buttonType) {
						"LIKE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
						"DISLIKE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_DISLIKE, Bundle.EMPTY))
						"SAVE" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_SAVE, Bundle.EMPTY))
						"BOOKMARK" -> builder.add(SessionCommand(Constants.CUSTOM_COMMAND_BOOKMARK, Bundle.EMPTY))
					}
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

				// Notification Button State Update Command
				builder.add(SessionCommand(Constants.CUSTOM_COMMAND_UPDATE_NOTIFICATION_STATE, Bundle.EMPTY))

				// Note: REPEAT_MODE and SHUFFLE_MODE now use standard Player commands
				// (COMMAND_SET_REPEAT_MODE, COMMAND_SET_SHUFFLE_MODE) instead of session commands
			}
			.build()

	@OptIn(UnstableApi::class)
	override fun onConnect(
		session: MediaSession,
		controller: MediaSession.ControllerInfo,
	): MediaSession.ConnectionResult {
		// Store session reference for dynamic button updates
		this.session = session

		// Explicitly declare available Player commands for consistency across Android versions
		val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
			.addAll(
				// Standard playback
				Player.COMMAND_PLAY_PAUSE,
				Player.COMMAND_PREPARE,
				Player.COMMAND_STOP,
				// Seeking
				Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
				Player.COMMAND_SEEK_TO_NEXT,
				Player.COMMAND_SEEK_TO_PREVIOUS,
				Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
				Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
				Player.COMMAND_SEEK_BACK,
				Player.COMMAND_SEEK_FORWARD,
				// Queue
				Player.COMMAND_GET_TIMELINE,
				Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
				Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
				Player.COMMAND_SET_MEDIA_ITEM,
				Player.COMMAND_CHANGE_MEDIA_ITEMS,
				// Playback parameters
				Player.COMMAND_SET_SPEED_AND_PITCH,
				Player.COMMAND_SET_REPEAT_MODE,
				Player.COMMAND_SET_SHUFFLE_MODE,
				Player.COMMAND_SET_VOLUME,
				// State
				Player.COMMAND_GET_AUDIO_ATTRIBUTES,
				Player.COMMAND_GET_VOLUME,
			)
			.build()

		return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
			.setAvailablePlayerCommands(availablePlayerCommands)
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

			// Note: REPEAT_MODE and SHUFFLE_MODE now handled via standard Player commands
			// (Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE)
			// No custom command handler needed - MediaBrowser.repeatMode/shuffleModeEnabled work directly

			Constants.CUSTOM_COMMAND_SET_SKIP_SILENCE -> {
				val enabled = args.getBoolean("enabled", false)
				service.handleSetSkipSilence(enabled)
				return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
			}

			Constants.CUSTOM_COMMAND_UPDATE_NOTIFICATION_STATE -> {
				// Update notification button states (like/dislike/bookmark)
				val liked = args.getBoolean("liked", false)
				val disliked = args.getBoolean("disliked", false)
				val bookmarked = args.getBoolean("bookmarked", false)
				updateButtonStates(liked, disliked, bookmarked)
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

	// ─────────────────────────────────────────────────────────────
	// Android Auto / Media Browser Tree
	// ─────────────────────────────────────────────────────────────
	// Android Auto requires a browsable media tree to display the app.
	// We provide a minimal root + "Now Playing" node that exposes
	// the current queue so Auto can render playback controls properly.

	companion object {
		private const val MEDIA_ROOT_ID = "root"
		private const val MEDIA_NOW_PLAYING_ID = "now_playing"
	}

	/**
	 * Notifies Android Auto that the "Now Playing" children list has changed.
	 * Call this when the queue is modified (add/remove/reorder) so Auto refreshes.
	 */
	fun notifyQueueChanged() {
		(session as? MediaLibraryService.MediaLibrarySession)?.let { libSession ->
			libSession.notifyChildrenChanged(MEDIA_NOW_PLAYING_ID, 0, null)
			AudioProController.log("Notified Android Auto: Now Playing children changed")
		}
	}

	override fun onGetLibraryRoot(
		session: MediaLibraryService.MediaLibrarySession,
		browser: MediaSession.ControllerInfo,
		params: MediaLibraryService.LibraryParams?,
	): ListenableFuture<LibraryResult<MediaItem>> {
		val rootExtras = Bundle().apply {
			putInt(
				MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_BROWSABLE_ONLY,
				1
			)
		}
		val rootItem = MediaItem.Builder()
			.setMediaId(MEDIA_ROOT_ID)
			.setMediaMetadata(
				MediaMetadata.Builder()
					.setTitle("Audio Pro")
					.setIsBrowsable(true)
					.setIsPlayable(false)
					.setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
					.setExtras(rootExtras)
					.build()
			)
			.build()
		return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
	}

	override fun onGetChildren(
		session: MediaLibraryService.MediaLibrarySession,
		browser: MediaSession.ControllerInfo,
		parentId: String,
		page: Int,
		pageSize: Int,
		params: MediaLibraryService.LibraryParams?,
	): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
		return when (parentId) {
			MEDIA_ROOT_ID -> {
				// Return a single "Now Playing" browsable folder
				val nowPlayingFolder = MediaItem.Builder()
					.setMediaId(MEDIA_NOW_PLAYING_ID)
					.setMediaMetadata(
						MediaMetadata.Builder()
							.setTitle("Now Playing")
							.setIsBrowsable(true)
							.setIsPlayable(false)
							.setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
							.build()
					)
					.build()
				Futures.immediateFuture(
					LibraryResult.ofItemList(listOf(nowPlayingFolder), params)
				)
			}
			MEDIA_NOW_PLAYING_ID -> {
				// Return the current queue items with pagination support
				val player = session.player
				val allItems = mutableListOf<MediaItem>()
				for (i in 0 until player.mediaItemCount) {
					val existingItem = player.getMediaItemAt(i)
					// Ensure each item is marked as playable for Android Auto
					val rebuiltItem = existingItem.buildUpon()
						.setMediaMetadata(
							existingItem.mediaMetadata.buildUpon()
								.setIsPlayable(true)
								.setIsBrowsable(false)
								.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
								.build()
						)
						.build()
					allItems.add(rebuiltItem)
				}

				// Apply pagination if requested
				val startIndex = if (pageSize > 0) page * pageSize else 0
				val endIndex = if (pageSize > 0) {
					minOf(startIndex + pageSize, allItems.size)
				} else {
					allItems.size
				}
				val pagedItems = if (startIndex < allItems.size) {
					allItems.subList(startIndex, endIndex)
				} else {
					emptyList()
				}

				Futures.immediateFuture(LibraryResult.ofItemList(pagedItems, params))
			}
			else -> {
				Futures.immediateFuture(
					LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
				)
			}
		}
	}

	override fun onGetItem(
		session: MediaLibraryService.MediaLibrarySession,
		browser: MediaSession.ControllerInfo,
		mediaId: String,
	): ListenableFuture<LibraryResult<MediaItem>> {
		// Check static nodes first
		if (mediaId == MEDIA_ROOT_ID || mediaId == MEDIA_NOW_PLAYING_ID) {
			val item = MediaItem.Builder()
				.setMediaId(mediaId)
				.setMediaMetadata(
					MediaMetadata.Builder()
						.setTitle(if (mediaId == MEDIA_ROOT_ID) "Audio Pro" else "Now Playing")
						.setIsBrowsable(true)
						.setIsPlayable(false)
						.build()
				)
				.build()
			return Futures.immediateFuture(LibraryResult.ofItem(item, null))
		}

		// Search queue for matching media ID
		val player = session.player
		for (i in 0 until player.mediaItemCount) {
			val queueItem = player.getMediaItemAt(i)
			if (queueItem.mediaId == mediaId) {
				val rebuiltItem = queueItem.buildUpon()
					.setMediaMetadata(
						queueItem.mediaMetadata.buildUpon()
							.setIsPlayable(true)
							.setIsBrowsable(false)
							.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
							.build()
					)
					.build()
				return Futures.immediateFuture(LibraryResult.ofItem(rebuiltItem, null))
			}
		}

		return Futures.immediateFuture(
			LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
		)
	}

	/**
	 * Subscribe to receive updates when children of a node change.
	 * Required for Android Auto to receive notifyChildrenChanged() callbacks.
	 */
	override fun onSubscribe(
		session: MediaLibraryService.MediaLibrarySession,
		browser: MediaSession.ControllerInfo,
		parentId: String,
		params: MediaLibraryService.LibraryParams?,
	): ListenableFuture<LibraryResult<Void>> {
		// Accept all subscriptions — we'll notify on queue changes
		return Futures.immediateFuture(LibraryResult.ofVoid())
	}

}
