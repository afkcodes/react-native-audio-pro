package dev.rnap.reactnativeaudiopro

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.facebook.react.bridge.ReactContext
import com.google.common.collect.ImmutableList

@UnstableApi
class AudioProNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    	override fun getMediaButtons(
        session: MediaSession,
        playerCommands: androidx.media3.common.Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        // This allows us to customize which buttons are shown in the notification.
        // For now, we rely on the default behavior which uses the session's available commands
        // and the custom layout we defined in the Callback.
        return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
    }
}
