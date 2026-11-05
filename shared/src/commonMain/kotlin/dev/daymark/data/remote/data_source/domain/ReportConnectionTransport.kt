package dev.daymark.data.remote.data_source.domain

import dev.daymark.models.reporting.ReportCreation
import dev.daymark.models.reporting.ReportDiscovery

interface ReportConnectionTransport : ReportTransport {
    suspend fun accountReference(token: String): String
    suspend fun discover(trackerId: String, token: String): ReportDiscovery
    suspend fun create(title: String, trackerId: String, token: String): ReportCreation
}
