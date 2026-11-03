package dev.daymark.location

import kotlin.test.Test

class AndroidLocationGatewayTest {
    @Test
    fun timeout_cancels_once_and_ignores_late_fix() = timeoutContract { AndroidLocationGateway(it) }
    @Test
    fun caller_cancellation_never_retries() = cancellationContract { AndroidLocationGateway(it) }
    @Test
    fun invalid_fix_is_not_success() = invalidDataContract { AndroidLocationGateway(it) }
    @Test
    fun denial_is_final_for_this_attempt() = denialContract { AndroidLocationGateway(it) }
    @Test
    fun successful_fix_cleans_up_the_native_request() =
        successContract { AndroidLocationGateway(it) }
}
