package dev.daymark.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build

internal actual fun isPinWidgetSupported(): Boolean {
    val ctx = runCatching { widgetContext() }.getOrNull() ?: return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = ctx.getSystemService(AppWidgetManager::class.java) ?: return false
    return manager.isRequestPinAppWidgetSupported
}

internal actual fun pinWidgetToHomeScreen(): Boolean {
    val ctx = runCatching { widgetContext() }.getOrNull() ?: return false
    val provider = ComponentName(ctx.packageName, "dev.daymark.android.DaymarkWidgetReceiver")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = ctx.getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            return manager.requestPinAppWidget(provider, null, null)
        }
    }
    return try {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(homeIntent)
        true
    } catch (_: Throwable) {
        false
    }
}
