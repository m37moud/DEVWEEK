package dev.daymark.app

import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual fun reducedMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
