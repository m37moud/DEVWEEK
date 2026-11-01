package dev.daymark.android

import android.content.Context
import android.util.AtomicFile
import dev.daymark.widgets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.File

/** Credential-protected, excluded-from-backup display cache. No database or native auth objects. */
class AndroidWidgetPublisher(context: Context,private val file: File = snapshotFile(context),
    private val onChanged: () -> Unit = { (context.applicationContext as DaymarkApplication).graph.requestWidgetUpdate() }) : WidgetSink {
    private val atomic = AtomicFile(file)
    override fun publish(json: String): Boolean = synchronized(fileLock) {
        val snapshot = decodeWidgetSnapshot(json) ?: return@synchronized false
        val normalized = encodeWidgetSnapshot(snapshot)
        var output: java.io.FileOutputStream? = null
        try {
            file.parentFile?.mkdirs()
            output = atomic.startWrite()
            output.write(normalized.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
            changes.update { it + 1 }
            onChanged()
            true
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
            false
        }
    }
    override fun clear(): Boolean = synchronized(fileLock) { try {
        atomic.delete()
        val removed = !file.exists()
        changes.update { it + 1 }
        onChanged()
        removed
    } catch (_: Exception) { false } }
    companion object {
        // AtomicFile.openRead may restore a backup; readers must share the writer lock on API 28 too.
        private val fileLock=Any()
        private val changes = MutableStateFlow(0L)
        private fun snapshotFile(context: Context) = File(context.noBackupFilesDir,"daymark-widget.json")
        fun readFile(file: File): WidgetSnapshot? = synchronized(fileLock) { try {
            if (file.length() > 262_144) null else AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { decodeWidgetSnapshot(it.readText()) }
        } catch (_: Exception) { null } }
        fun observe(context: Context): Flow<WidgetSnapshot?> = changes.map { readFile(snapshotFile(context)) }.flowOn(Dispatchers.IO)
    }
}
