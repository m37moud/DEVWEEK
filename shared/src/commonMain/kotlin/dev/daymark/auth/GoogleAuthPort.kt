package dev.daymark.auth

val GOOGLE_REPORT_SCOPES: Set<String> = setOf("https://www.googleapis.com/auth/drive.file")

enum class NativeAuthStatus { GRANTED, NEEDS_INTERACTION, CANCELLED, FAILED }

class NativeAuthReply(val status: NativeAuthStatus, val accessToken: String?, val errorKey: String?) {
    override fun toString(): String = "NativeAuthReply(status=$status, accessToken=<redacted>)"
}

/** Native adapters own SDK sessions and consent UI; never persist replies in app storage. */
interface NativeGoogleAuth {
    fun authorize(interactive: Boolean, completion: (NativeAuthReply) -> Unit)
    fun forget(completion: (Boolean) -> Unit)
}
