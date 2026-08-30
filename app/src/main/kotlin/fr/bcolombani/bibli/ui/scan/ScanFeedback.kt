package fr.bcolombani.bibli.ui.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Les quatre issues d'un scan, du point de vue du retour utilisateur.
 *
 * L'icône est **toujours** affichée : c'est le canal principal. Le son et la vibration
 * ne sont qu'un bonus, un téléphone muet ne doit rien coûter en information.
 */
enum class ScanFeedbackKind {
    /** ISBN valide et trouvé par une API → coche verte. */
    ADDED,

    /** ISBN déjà en base → coche bleue. */
    DUPLICATE,

    /** ISBN valide mais introuvable → warning orange, saisie manuelle. */
    NOT_FOUND,

    /** Code lu mais pas un ISBN → croix rouge. */
    REJECTED,
}

/**
 * Son ([ToneGenerator]) + vibration ([VibrationEffect]), avec un motif distinct par issue.
 *
 * Toutes les opérations sont enveloppées : un téléphone sans vibreur, un flux audio
 * indisponible ou un [ToneGenerator] refusé ne doivent jamais interrompre le scan.
 */
class ScanFeedback(context: Context) {

    private val appContext = context.applicationContext

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
    }.onFailure { Log.w(TAG, "ToneGenerator indisponible", it) }.getOrNull()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    fun play(kind: ScanFeedbackKind) {
        playTone(kind)
        vibrate(kind)
    }

    private fun playTone(kind: ScanFeedbackKind) {
        val generator = toneGenerator ?: return
        val (tone, durationMs) = when (kind) {
            // Double bip montant : « c'est dans la boîte ».
            ScanFeedbackKind.ADDED -> ToneGenerator.TONE_PROP_ACK to 180
            // Double bip court et sec, volontairement différent du vert.
            ScanFeedbackKind.DUPLICATE -> ToneGenerator.TONE_PROP_BEEP2 to 160
            // Bip simple, neutre : « à toi de jouer ».
            ScanFeedbackKind.NOT_FOUND -> ToneGenerator.TONE_PROP_BEEP to 150
            // Tonalité d'erreur, grave.
            ScanFeedbackKind.REJECTED -> ToneGenerator.TONE_SUP_ERROR to 300
        }
        runCatching { generator.startTone(tone, durationMs) }
    }

    private fun vibrate(kind: ScanFeedbackKind) {
        val vibe = vibrator ?: return
        val effect = when (kind) {
            ScanFeedbackKind.ADDED -> VibrationEffect.createOneShot(50, DEFAULT_AMPLITUDE)
            ScanFeedbackKind.DUPLICATE ->
                VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 30), -1)
            ScanFeedbackKind.NOT_FOUND ->
                VibrationEffect.createWaveform(longArrayOf(0, 140), -1)
            ScanFeedbackKind.REJECTED ->
                VibrationEffect.createWaveform(longArrayOf(0, 90, 80, 90, 80, 90), -1)
        }
        runCatching { vibe.vibrate(effect) }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
    }

    private companion object {
        const val TAG = "ScanFeedback"
        const val TONE_VOLUME = 90
        const val DEFAULT_AMPLITUDE = VibrationEffect.DEFAULT_AMPLITUDE
    }
}
