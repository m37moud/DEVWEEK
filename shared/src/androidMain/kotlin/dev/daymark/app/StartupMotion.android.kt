package dev.daymark.app

import android.animation.ValueAnimator

internal actual fun reducedMotionEnabled(): Boolean = !ValueAnimator.areAnimatorsEnabled()
