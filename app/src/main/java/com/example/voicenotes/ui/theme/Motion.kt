package com.example.voicenotes.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Shared motion tokens for M3 Expressive-style transitions.
 * Prefer springs for UI feedback; use [navTween] for navigation slides.
 */
@Immutable
object VoiceNotesMotion {
    /** Emphasized decelerate — enters that settle with presence. */
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Emphasized accelerate — exits that leave quickly. */
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Standard M3 easing for short UI feedback. */
    val standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val shortDurationMs = 200
    const val mediumDurationMs = 300
    const val longDurationMs = 500

    fun <T> navTween(durationMillis: Int = mediumDurationMs): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = emphasizedDecelerate)

    fun <T> fadeTween(durationMillis: Int = shortDurationMs): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = standard)

    /** Soft spring for FAB pulse, chips, and press feedback. */
    fun <T> expressiveSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Snappier spring for toggles and small controls. */
    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
