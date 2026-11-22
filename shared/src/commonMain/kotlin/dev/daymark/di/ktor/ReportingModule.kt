package dev.daymark.di.ktor

import dev.daymark.auth.GoogleAccess
import dev.daymark.data.local.reporting.ReportingRuntime
import dev.daymark.data.local.reporting.RoomReportStore
import dev.daymark.data.remote.data_source.data.ktor.GoogleReportTransport
import dev.daymark.data.remote.data_source.domain.ReportConnectionTransport
import dev.daymark.data.remote.data_source.domain.ReportTransport
import dev.daymark.repository.ReportingDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.dsl.module
import org.koin.dsl.onClose

internal fun reportingModule() = module {
    single { GoogleAccess(get()) }
    single { RoomReportStore(get(), get(), get(), get(), get(), get(), get()) }
    single {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
    single { GoogleReportTransport(get()) }
    single<ReportConnectionTransport> { get<GoogleReportTransport>() }
    single<ReportTransport> { get<GoogleReportTransport>() }
    single {
        ReportingRuntime(
            profileDao = get(),
            planningDao = get(),
            dirtyProjectionDao = get(),
            reportDao = get(),
            transactions = get(),
            auth = get(),
            clock = get(),
            repository = get(),
            httpClient = get(),
            store = get(),
            reportTransport = get()
        )
    } onClose { it?.close() }
    single<ReportingDataSource> { get<ReportingRuntime>() }
}
