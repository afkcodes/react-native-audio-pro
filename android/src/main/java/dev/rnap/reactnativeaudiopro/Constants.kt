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
}
