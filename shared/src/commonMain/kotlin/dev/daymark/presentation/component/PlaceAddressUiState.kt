package dev.daymark.presentation.component

import dev.daymark.location.PlaceAddressRequest
import dev.daymark.repository.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

data class PlaceAddressUiState(
    val request: PlaceAddressRequest? = null,
    val address: String? = null,
    val loading: Boolean = false
) {
    override fun toString() = "PlaceAddressUiState(address=<redacted>, loading=$loading)"
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Repository.placeAddressStates(request: PlaceAddressRequest): Flow<PlaceAddressUiState> = flow {
    var deleted = false
    emitAll(dataLifecycle.deleting.transformLatest { deleting ->
        deleted = deleted || deleting
        if (deleted) {
            emit(PlaceAddressUiState())
        } else {
            emit(PlaceAddressUiState(request, loading = true))
            emit(PlaceAddressUiState(request, placeAddresses.resolve(request)))
        }
    })
}
