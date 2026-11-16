package com.vortessence.mahjong.audio

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator

object HapticPatterns {

    const val MAX_MATCH_STREAK = 3

    /**
     * Vendor-tuned primitive or predefined effect, not a raw pulse: a short low-amplitude one-shot is
     * mostly motor spin-up/down and feels blurry.
     */
    fun select(vibrator: Vibrator): VibrationEffect =
        crisp(vibrator, Composition.PRIMITIVE_CLICK, 0.35f, VibrationEffect.EFFECT_CLICK)

    /** Lighter than [select], so the two can be told apart by feel. */
    fun deselect(vibrator: Vibrator): VibrationEffect =
        crisp(vibrator, Composition.PRIMITIVE_TICK, 0.4f, VibrationEffect.EFFECT_TICK)

    fun blocked(): VibrationEffect = VibrationEffect.createOneShot(25, 40)

    /** Fired as the tiles collide: clack and two rebounds; a higher [streak] hits harder with extra ticks. */
    fun match(vibrator: Vibrator, streak: Int): VibrationEffect {
        val level = streak.coerceIn(0, MAX_MATCH_STREAK)
        val boost = level * 0.08f

        if (vibrator.areAllPrimitivesSupported(Composition.PRIMITIVE_CLICK, Composition.PRIMITIVE_TICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(Composition.PRIMITIVE_CLICK, (0.8f + boost).coerceAtMost(1f))
                .addPrimitive(Composition.PRIMITIVE_CLICK, 0.4f + boost, 45)
                .addPrimitive(Composition.PRIMITIVE_TICK, 0.3f + boost, 40)
            repeat(level) { i ->
                composition.addPrimitive(Composition.PRIMITIVE_TICK, 0.35f + (0.15f * i), 30)
            }
            return composition.compose()
        }

        val timings = mutableListOf(0L, 20L, 45L, 12L, 40L, 8L)
        val amplitudes = mutableListOf(0, 200 + (level * 18), 0, 120 + (level * 15), 0, 70 + (level * 15))
        repeat(level) { i ->
            timings += listOf(30L, 8L)
            amplitudes += listOf(0, 80 + (40 * i))
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
    }

    /** Follows a pentatonic chime, a note every 160 ms: four rising taps, then a strong fading hit. */
    fun win(vibrator: Vibrator): VibrationEffect {
        if (!vibrator.hasAmplitudeControl()) {
            // On/off motors: keep the rhythm, skip the fading tail
            return VibrationEffect.createWaveform(
                longArrayOf(0, 18, 142, 18, 142, 18, 142, 18, 142, 70),
                -1,
            )
        }
        return VibrationEffect.createWaveform(
            longArrayOf(0, 22, 138, 22, 138, 22, 138, 22, 138, 35, 50, 70, 90),
            intArrayOf(0, 70, 0, 100, 0, 135, 0, 170, 0, 255, 140, 70, 30),
            -1,
        )
    }

    private fun crisp(vibrator: Vibrator, primitive: Int, scale: Float, fallbackEffect: Int): VibrationEffect {
        if (vibrator.areAllPrimitivesSupported(primitive)) {
            return VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        }
        return VibrationEffect.createPredefined(fallbackEffect)
    }
}
