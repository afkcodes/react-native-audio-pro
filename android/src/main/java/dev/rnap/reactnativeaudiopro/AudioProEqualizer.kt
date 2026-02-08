package dev.rnap.reactnativeaudiopro

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.Eq
import android.media.audiofx.DynamicsProcessing.EqBand
import android.os.Build
import android.util.Log

class AudioProEqualizer {
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = -1
    
    // Default 10-band config matching user reference if possible
    // 31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k
    private val defaultBands = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private var currentGains: FloatArray? = null
    private var currentBassStrength: Int = 0

    fun initialize(audioSessionId: Int) {
        if (currentSessionId == audioSessionId) return
        release()
        currentSessionId = audioSessionId

        if (audioSessionId == 0) return // Invalid session

        try {
            // Initialize BassBoost (Available since API 9)
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = currentBassStrength > 0
                setStrength(currentBassStrength.toShort())
            }

            // Initialize DynamicsProcessing (Available since API 28)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val bandCount = defaultBands.size
                
                // DynamicsProcessing.Config.Builder:
                // variant, channelCount, preEqInUse, preEqBandCount, mbcInUse, mbcBandCount, postEqInUse, postEqBandCount, limiterInUse
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // channels (stereo)
                    false, 0,        // preEq (Disabled to avoid conflict)
                    false, 0,        // mbc
                    true, bandCount, // postEq
                    true             // limiter
                )

                // Configure Eq bands
                val eqConfig = Eq(true, true, bandCount)
                for (i in 0 until bandCount) {
                    val freq = defaultBands[i].toFloat()
                    val band = EqBand(true, freq, 0.0f) // cutoff, gain
                    eqConfig.setBand(i, band)
                }
                
                // Apply to both channels
                // builder.setPreEqByChannelIndex(0, eqConfig)
                // builder.setPreEqByChannelIndex(1, eqConfig)
                builder.setPostEqByChannelIndex(0, eqConfig)
                builder.setPostEqByChannelIndex(1, eqConfig)

                dynamicsProcessing = DynamicsProcessing(0, audioSessionId, builder.build())
                dynamicsProcessing?.enabled = true
                
                Log.i(Constants.LOG_TAG, "DynamicsProcessing initialized for session $audioSessionId (2 channels)")
                
                // Apply cached gains if any
                currentGains?.let { applyGains(it) }
            } else {
                Log.w(Constants.LOG_TAG, "DynamicsProcessing not supported on this device (API < 28)")
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "Failed to initialize audio effects", e)
        }
    }

    fun setGains(gains: FloatArray) {
        currentGains = gains
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                 applyGains(gains)
            }
        } else {
            // Fallback for older devices? 
            // Standard Equalizer has fixed bands, mapping 10/8 bands to 5 is complex.
            // For now, we log warning.
            if (gains.isNotEmpty()) {
                 Log.w(Constants.LOG_TAG, "Equalizer ignored: API level too low")
            }
        }
    }
    
    // Internal helper to apply gains to DP
    private fun applyGains(gains: FloatArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing?.let { dp ->
                // Apply to all configured channels (we use 2)
                for (ch in 0 until 2) {
                    val postEq = dp.getPostEqByChannelIndex(ch)
                    if (postEq != null) {
                        for (i in 0 until minOf(gains.size, postEq.bandCount)) {
                            val band = postEq.getBand(i)
                            band.gain = gains[i]
                            postEq.setBand(i, band)
                        }
                        dp.setPostEqByChannelIndex(ch, postEq)
                    }
                }
            }
        }
    }

    fun setBassBoost(strength: Int) {
        currentBassStrength = strength
        bassBoost?.let {
            it.setStrength(strength.toShort())
            it.enabled = strength > 0
        }
    }

    fun release() {
        try {
            bassBoost?.release()
            dynamicsProcessing?.release()
        } catch (e: Exception) {
            // Ignore
        }
        bassBoost = null
        dynamicsProcessing = null
        currentSessionId = -1
    }
}
