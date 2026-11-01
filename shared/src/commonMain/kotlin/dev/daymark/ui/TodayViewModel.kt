package dev.daymark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.daymark.attendance.*
import dev.daymark.location.LocationPermissionPort
import dev.daymark.plan.PlanReader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Clock

/** Screen observations own no sensor loop. The repository owns arrival/deadline transitions. */
data class TodayUiState(
    val date: LocalDate? = null,
    val plan: DayPlan? = null,
    val outcome: DayOutcome? = null,
    val busy: Boolean = false,
    val canCheck: Boolean = false,
    val error: CheckRejection? = null,
    val ready: Boolean = false,
    val active: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: AttendanceRepository,
    private val attendance: AttendanceService,
    private val plans: PlanReader,
    private val permissions: LocationPermissionPort,
    private val clock: Clock,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    initiallyActive: Boolean = true,
) : ViewModel(scope) {
    private val state = MutableStateFlow(TodayUiState(active = initiallyActive))
    val uiState = state.asStateFlow()
    private val active = MutableStateFlow(initiallyActive)
    private val refresh = Channel<Unit>(Channel.CONFLATED)
    private var check: Job? = null

    init {
        viewModelScope.launch {
            active.flatMapLatest { enabled ->
                if (!enabled) emptyFlow() else flow {
                    emit(Unit)
                    emitAll(refresh.receiveAsFlow())
                }.flatMapLatest {
                    flow {
                        val now = clock.now()
                        repository.reconcile(now)
                        val context = plans.context()
                        val day = repository.loadCurrentDay(now)
                        if (context == null || day == null) {
                            state.value = TodayUiState(ready = true, error = CheckRejection.NO_TRACKER, active = active.value)
                        } else coroutineScope {
                            val zone = TimeZone.of(day.plan.zoneId)
                            val tomorrow = day.plan.key.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
                            launch { delay((tomorrow - clock.now()).coerceAtLeast(kotlin.time.Duration.ZERO)); refresh.send(Unit) }
                            repository.observeDay(day.plan.key).collect { observed ->
                                val stored = repository.loadDay(day.plan.key)
                                val hiddenBaseline = stored == null && day.plan.isScheduled && day.off==null && day.plan.arrival < context.trackingStartsAt
                                val outcome = if (hiddenBaseline) null else observed ?: evaluateDay(day.plan, day.evidence, day.off, clock.now())
                                state.update { old ->
                                    val sameDay = old.plan?.key == day.plan.key
                                    val next = old.copy(date = day.plan.key.date, plan = day.plan, outcome = outcome,
                                        ready = true, error = if (sameDay) old.error else null)
                                    next.copy(canCheck = canCheck(next, (stored ?: day).off != null))
                                }
                            }
                        }
                        emit(Unit)
                    }.catch { error ->
                        if (error is CancellationException) throw error
                        state.update { it.copy(ready = true, canCheck = false, error = CheckRejection.LOCAL_SAVE_FAILED) }
                    }
                }
            }.collect()
        }
    }

    private fun canCheck(value: TodayUiState, hasTimeOff: Boolean = false): Boolean {
        val plan = value.plan ?: return false
        return active.value && !value.busy && plan.isScheduled && !hasTimeOff && clock.now() >= plan.arrival &&
            value.outcome?.status !in listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.DAY_OFF, AttendanceStatus.HOLIDAY, AttendanceStatus.VACATION) &&
            value.outcome?.source != AttendanceSource.MANUAL_CORRECTION
    }

    fun setActive(value: Boolean) {
        if (active.value == value) return
        active.value = value
        state.update { it.copy(active = value, ready = if (value) false else it.ready, canCheck = false) }
        if (!value) {
            check?.cancel()
            state.update { it.copy(canCheck = false) }
        }
    }

    fun requestQuickCheck(expectedTrackerId: String? = null) {
        val current = state.value
        if (!current.ready || !current.active || current.busy) return
        val plan = current.plan ?: return
        if (expectedTrackerId != null && expectedTrackerId != plan.key.trackerId) return
        if (current.canCheck) checkAttendance()
        else if (clock.now() < plan.arrival) state.update { it.copy(error = CheckRejection.BEFORE_WINDOW) }
    }

    fun openLocationSettings() = permissions.openSystemSettings()

    fun checkAttendance() {
        val initial = state.value
        if (!initial.canCheck || initial.busy) return
        val key = initial.plan?.key ?: return
        state.update { it.copy(busy = true, canCheck = false, error = null) }
        check = viewModelScope.launch {
            try {
                var capabilities = permissions.current()
                if (!capabilities.foregroundGranted || !capabilities.preciseGranted) capabilities = permissions.requestForeground()
                val result = when {
                    !capabilities.foregroundGranted || !capabilities.preciseGranted -> CheckOutcome.Rejected(null, CheckRejection.LOCATION_DENIED)
                    !capabilities.servicesEnabled -> CheckOutcome.Rejected(null, CheckRejection.LOCATION_DISABLED)
                    else -> attendance.manualCheck()
                }
                state.update { old ->
                    if (old.plan?.key != key) old else old.copy(outcome = result.currentDay ?: old.outcome, error = result.reason)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(error = CheckRejection.LOCAL_SAVE_FAILED) } }
            finally {
                state.update { old -> old.copy(busy = false).let { it.copy(canCheck = canCheck(it)) } }
            }
        }
    }
}
