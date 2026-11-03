package dev.daymark.location

import kotlin.test.Test

class IosLocationGatewayTest {
    @Test
    fun timeout_cancels_once_and_ignores_late_fix() = timeoutContract { IosLocationGateway(it) }
    @Test
    fun caller_cancellation_never_retries() = cancellationContract { IosLocationGateway(it) }
    @Test
    fun invalid_fix_is_not_success() = invalidDataContract { IosLocationGateway(it) }
    @Test
    fun denial_is_final_for_this_attempt() = denialContract { IosLocationGateway(it) }
    @Test
    fun successful_fix_cleans_up_the_native_request() = successContract { IosLocationGateway(it) }
}
