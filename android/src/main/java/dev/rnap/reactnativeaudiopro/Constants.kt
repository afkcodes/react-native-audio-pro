package dev.rnap.reactnativeaudiopro

object Constants {
    const val LOG_TAG = "AudioPro"
    const val NOTIFICATION_ID = 789
    const val NOTIFICATION_CHANNEL_ID = "audio_pro_notification_channel_id"
    const val PENDING_INTENT_REQUEST_CODE = 0

    // Custom Commands
    const val CUSTOM_COMMAND_NEXT = "dev.rnap.reactnativeaudiopro.NEXT"
    const val CUSTOM_COMMAND_PREV = "dev.rnap.reactnativeaudiopro.PREV"
    const val CUSTOM_COMMAND_SKIP_FORWARD = "dev.rnap.reactnativeaudiopro.SKIP_FORWARD"
    const val CUSTOM_COMMAND_SKIP_BACKWARD = "dev.rnap.reactnativeaudiopro.SKIP_BACKWARD"
    
    // Ambient Commands
    const val CUSTOM_COMMAND_AMBIENT_PLAY = "dev.rnap.reactnativeaudiopro.AMBIENT_PLAY"
    const val CUSTOM_COMMAND_AMBIENT_STOP = "dev.rnap.reactnativeaudiopro.AMBIENT_STOP"
    const val CUSTOM_COMMAND_AMBIENT_PAUSE = "dev.rnap.reactnativeaudiopro.AMBIENT_PAUSE"
    const val CUSTOM_COMMAND_AMBIENT_RESUME = "dev.rnap.reactnativeaudiopro.AMBIENT_RESUME"
    const val CUSTOM_COMMAND_AMBIENT_SEEK = "dev.rnap.reactnativeaudiopro.AMBIENT_SEEK"
    const val CUSTOM_COMMAND_AMBIENT_SET_VOLUME = "dev.rnap.reactnativeaudiopro.AMBIENT_SET_VOLUME"

    // DSP Commands
    const val CUSTOM_COMMAND_SET_EQUALIZER = "dev.rnap.reactnativeaudiopro.SET_EQUALIZER"
    const val CUSTOM_COMMAND_SET_BASS_BOOST = "dev.rnap.reactnativeaudiopro.SET_BASS_BOOST"

    // Playlist Commands
    const val CUSTOM_COMMAND_SET_REPEAT_MODE = "dev.rnap.reactnativeaudiopro.SET_REPEAT_MODE"
    const val CUSTOM_COMMAND_SET_SHUFFLE_MODE = "dev.rnap.reactnativeaudiopro.SET_SHUFFLE_MODE"
    const val CUSTOM_COMMAND_SET_SKIP_SILENCE = "dev.rnap.reactnativeaudiopro.SET_SKIP_SILENCE"
    const val CUSTOM_COMMAND_UPDATE_TRACK = "dev.rnap.reactnativeaudiopro.UPDATE_TRACK"
    
    // Custom Notification Action Commands
    const val CUSTOM_COMMAND_LIKE = "dev.rnap.reactnativeaudiopro.LIKE"
    const val CUSTOM_COMMAND_DISLIKE = "dev.rnap.reactnativeaudiopro.DISLIKE"
    const val CUSTOM_COMMAND_SAVE = "dev.rnap.reactnativeaudiopro.SAVE"
    const val CUSTOM_COMMAND_BOOKMARK = "dev.rnap.reactnativeaudiopro.BOOKMARK"
    const val CUSTOM_COMMAND_REWIND_30 = "dev.rnap.reactnativeaudiopro.REWIND_30"
    const val CUSTOM_COMMAND_FORWARD_30 = "dev.rnap.reactnativeaudiopro.FORWARD_30"
}
