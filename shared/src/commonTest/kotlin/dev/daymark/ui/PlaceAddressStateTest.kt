package dev.daymark.ui

import dev.daymark.TestRepository
import dev.daymark.location.PlaceAddressRequest
import dev.daymark.presentation.component.PlaceAddressUiState
import dev.daymark.presentation.component.placeAddressStates
import dev.daymark.repository.PlaceAddressDataSource
import dev.daymark.repository.DataLifecycleDataSource
import dev.daymark.settings.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceAddressStateTest {
    @Test
    fun changing_request_cancels_old_lookup_and_deletion_clears_and_stops_address_state() = runTest {
        val deleting = MutableStateFlow(false)
        val replies = mutableMapOf<PlaceAddressRequest, CompletableDeferred<String?>>()
        val repository = object : TestRepository() {
            override val placeAddresses = PlaceAddressDataSource { request ->
                replies.getOrPut(request) { CompletableDeferred() }.await()
            }
            override val dataLifecycle = object : DataLifecycleDataSource {
                override val deleting = deleting
                override suspend fun deleteLocalData(): Nothing = error("Unexpected deletion")
            }
        }
        val first = PlaceAddressRequest(30.0, 31.0, AppLanguage.EN)
        val second = first.copy(language = AppLanguage.AR)
        val requests = MutableStateFlow(first)
        var shown = PlaceAddressUiState()
        backgroundScope.launch {
            requests.flatMapLatest(repository::placeAddressStates).collect { shown = it }
        }
        runCurrent()
        assertTrue(shown.loading)
        requests.value = second
        runCurrent()
        replies.getValue(first).complete("Old street")
        runCurrent()
        assertEquals(second, shown.request)
        assertNull(shown.address)
        replies.getValue(second).complete("شارع تجريبي")
        runCurrent()
        assertEquals("شارع تجريبي", shown.address)
        val third = second.copy(latitude = 31.0)
        requests.value = third
        runCurrent()
        assertTrue(shown.loading)
        deleting.value = true
        runCurrent()
        replies.getValue(third).complete("Late street")
        runCurrent()
        assertEquals(PlaceAddressUiState(), shown)
        deleting.value = false
        runCurrent()
        assertFalse(shown.loading)
        assertNull(shown.address)
    }
}
