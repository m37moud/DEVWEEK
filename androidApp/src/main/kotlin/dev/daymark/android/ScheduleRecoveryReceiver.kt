package dev.daymark.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** System recovery only; permission prompts always belong to foreground UI. */
class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        val graph = (context.applicationContext as DaymarkApplication).graph
        graph.scope.launch {
            try { graph.recoverSafely() } finally { pending.finish() }
        }
    }
}
