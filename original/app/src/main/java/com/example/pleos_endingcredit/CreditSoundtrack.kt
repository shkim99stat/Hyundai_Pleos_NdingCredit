package com.example.pleos_endingcredit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer

/** Plays a bundled raw/credit_soundtrack when supplied. The design contains no audio asset,
 * so the current build remains silent. Muting is local to this player, never system volume.
 */
internal class CreditSoundtrack(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var request: AudioFocusRequest? = null
    private var enabled = false
    private var suspended = false
    private var hasFocus = false
    private var source = 0

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) { pause(); abandonFocus(); return }
        if (suspended) return
        if (source == 0) source = context.resources.getIdentifier("credit_soundtrack", "raw", context.packageName)
        if (source == 0) return
        if (!hasFocus) {
            val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { state ->
                    when (state) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            hasFocus = true
                            player?.setVolume(1f, 1f)
                            if (enabled && !suspended) startPlayer()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(.2f, .2f)
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { hasFocus = false; pause() }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            hasFocus = false; suspended = true; pause(); abandonFocus()
                        }
                    }
                }.build()
            request = next
            hasFocus = audio.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (hasFocus) startPlayer()
    }

    /** Called only for an explicit unmute/replay or a new foreground session. */
    fun allowResume() { suspended = false }
    fun release() { enabled = false; pause(); player?.release(); player = null; abandonFocus() }

    private fun startPlayer() {
        if (player == null) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    context.resources.openRawResourceFd(source).use { fd ->
                        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    isLooping = true
                    setOnErrorListener { _, _, _ -> this@CreditSoundtrack.release(); true }
                    prepare()
                }
            }.getOrNull()
        }
        runCatching { player?.start() }
    }
    private fun pause() { runCatching { if (player?.isPlaying == true) player?.pause() } }
    private fun abandonFocus() {
        request?.let { audio.abandonAudioFocusRequest(it) }; request = null; hasFocus = false
    }
}
