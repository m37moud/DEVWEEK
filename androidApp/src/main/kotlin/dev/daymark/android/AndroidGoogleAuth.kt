package dev.daymark.android

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.daymark.auth.*
import dev.daymark.auth.GOOGLE_REPORT_SCOPES
import dev.daymark.auth.NativeAuthReply
import dev.daymark.auth.NativeAuthStatus
import dev.daymark.auth.NativeGoogleAuth
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal sealed interface AndroidAuthAttempt {
    class Ready(val reply: NativeAuthReply) : AndroidAuthAttempt
    class Resolution(val launch: ((NativeAuthReply) -> Unit) -> Unit) : AndroidAuthAttempt
}
internal interface AndroidAuthorizationActions {
    fun request(scopes: Set<String>, completion: (AndroidAuthAttempt) -> Unit)
    fun reset(completion: (Boolean) -> Unit)
    fun close()
}

/** The application retains this port; bind an activity before STARTED, as for permissions. */
class AndroidGoogleAuth internal constructor(initial: AndroidAuthorizationActions) : NativeGoogleAuth {
    @Volatile private var actions: AndroidAuthorizationActions? = initial
    private val generation = AtomicLong()
    constructor(context: Context) : this(NativeAuthorizationActions(context.applicationContext))
    constructor(activity: ComponentActivity) : this(NativeAuthorizationActions(activity.applicationContext, activity)) {
        observe(activity, requireNotNull(actions))
    }
    fun attach(activity: ComponentActivity) {
        val next = NativeAuthorizationActions(activity.applicationContext, activity)
        bindActions(next); observe(activity, next)
    }
    internal fun bindActions(next: AndroidAuthorizationActions) {
        generation.incrementAndGet()
        val old = actions; actions = next; old?.close()
    }
    internal fun detachActions(bound: AndroidAuthorizationActions, fallback: AndroidAuthorizationActions) {
        if (actions === bound) bindActions(fallback) else fallback.close()
    }
    private fun observe(activity: ComponentActivity, bound: AndroidAuthorizationActions) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                detachActions(bound, NativeAuthorizationActions(activity.applicationContext))
            }
        })
    }
    override fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit) {
        val requestedGeneration = generation.get()
        val completed = AtomicBoolean()
        val dispatched = AtomicBoolean()
        fun finish(reply: NativeAuthReply) {
            if (!completed.compareAndSet(false, true)) return
            completion(if (generation.get() == requestedGeneration) reply else needsInteraction())
        }
        val bound = actions ?: run { finish(needsInteraction()); return }
        try {
            bound.request(GOOGLE_REPORT_SCOPES) { attempt ->
                if (dispatched.compareAndSet(false, true)) when (attempt) {
                    is AndroidAuthAttempt.Ready -> finish(attempt.reply)
                    is AndroidAuthAttempt.Resolution -> if (!interactive) finish(needsInteraction())
                        else try { attempt.launch(::finish) } catch (_: Exception) { finish(failed()) }
                }
            }
        } catch (_: Exception) { finish(failed()) }
    }
    override fun forget(completion: (Boolean) -> Unit) {
        generation.incrementAndGet()
        val completed = AtomicBoolean()
        fun finish(success: Boolean) { if (completed.compareAndSet(false, true)) completion(success) }
        try { actions?.reset(::finish) ?: finish(true) } catch (_: Exception) { finish(false) }
    }
}

private fun needsInteraction() = NativeAuthReply(NativeAuthStatus.NEEDS_INTERACTION, null, null)
private fun failed() = NativeAuthReply(NativeAuthStatus.FAILED, null, "authorization_failed")

private class NativeAuthorizationActions(context: Context, activity: ComponentActivity? = null) : AndroidAuthorizationActions {
    private val context = context.applicationContext
    private val client = Identity.getAuthorizationClient(context)
    private val main = Executor { Handler(Looper.getMainLooper()).post(it) }
    private val onClose = mutableSetOf<() -> Unit>()
    private var closed = false
    private var consent: ((NativeAuthReply) -> Unit)? = null
    private val launcher = activity?.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val finish = consent ?: return@registerForActivityResult
        consent = null
        if (result.resultCode == Activity.RESULT_CANCELED) finish(NativeAuthReply(NativeAuthStatus.CANCELLED, null, null))
        else try {
            val intent = result.data
            finish(if (intent == null) failed() else reply(client.getAuthorizationResultFromIntent(intent)))
        } catch (_: Exception) { finish(failed()) }
    }

    override fun request(scopes: Set<String>, completion: (AndroidAuthAttempt) -> Unit) {
        main.execute {
            if (closed) { completion(AndroidAuthAttempt.Ready(needsInteraction())); return@execute }
            val completeOnce = AtomicBoolean()
            lateinit var cancel: () -> Unit
            fun finish(attempt: AndroidAuthAttempt) {
                if (completeOnce.compareAndSet(false, true)) { onClose.remove(cancel); completion(attempt) }
            }
            cancel = { finish(AndroidAuthAttempt.Ready(needsInteraction())) }
            onClose += cancel
            try {
                client.authorize(AuthorizationRequest.builder().setRequestedScopes(scopes.map(::Scope)).build())
                    .addOnSuccessListener { result -> main.execute {
                        if (closed) cancel()
                        else if (result.hasResolution()) {
                            val intent = result.pendingIntent
                            finish(if (intent == null) AndroidAuthAttempt.Ready(failed())
                                else AndroidAuthAttempt.Resolution { launch(intent, it) })
                        } else finish(AndroidAuthAttempt.Ready(reply(result)))
                    } }
                    .addOnFailureListener { main.execute { finish(AndroidAuthAttempt.Ready(failed())) } }
            } catch (_: Exception) { finish(AndroidAuthAttempt.Ready(failed())) }
        }
    }
    private fun launch(intent: PendingIntent, completion: (NativeAuthReply) -> Unit) {
        main.execute {
            val currentLauncher = launcher
            if (closed || consent != null || currentLauncher == null) { completion(needsInteraction()); return@execute }
            val completeOnce = AtomicBoolean()
            lateinit var cancel: () -> Unit
            val finish: (NativeAuthReply) -> Unit = { result ->
                if (completeOnce.compareAndSet(false, true)) { onClose.remove(cancel); consent = null; completion(result) }
            }
            cancel = { finish(needsInteraction()) }
            onClose += cancel
            consent = finish
            try { currentLauncher.launch(IntentSenderRequest.Builder(intent).build()) }
            catch (_: Exception) { finish(failed()) }
        }
    }
    private fun reply(result: AuthorizationResult): NativeAuthReply {
        val token = result.accessToken
        val granted = result.grantedScopes.toSet()
        return if (!token.isNullOrBlank() && granted.containsAll(GOOGLE_REPORT_SCOPES))
            NativeAuthReply(NativeAuthStatus.GRANTED, token, null) else failed()
    }
    override fun reset(completion: (Boolean) -> Unit) {
        main.execute {
            try { CredentialManager.create(context).clearCredentialStateAsync(ClearCredentialStateRequest(), null, main,
                object : CredentialManagerCallback<Void?, ClearCredentialException> {
                    override fun onResult(result: Void?) { completion(true) }
                    override fun onError(e: ClearCredentialException) { completion(false) }
                }) } catch (_: Exception) { completion(false) }
        }
    }
    override fun close() {
        main.execute {
            closed = true
            onClose.toList().forEach { it() }; onClose.clear()
            launcher?.unregister()
        }
    }
}
