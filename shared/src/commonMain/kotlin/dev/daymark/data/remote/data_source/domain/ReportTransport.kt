package dev.daymark.data.remote.data_source.domain

import dev.daymark.models.reporting.RawPatch
import dev.daymark.models.reporting.RemoteReport
import dev.daymark.models.reporting.RemoteSnapshot
import dev.daymark.models.reporting.RemoteWriteOutcome

interface ReportTransport {
    suspend fun prepareTemplate(
        report: RemoteReport,
        trackerId: String,
        zoneId: String,
        years: Set<Int>,
        token: String
    ): RemoteReport

    suspend fun read(report: RemoteReport, ranges: List<String>, token: String): RemoteSnapshot
    suspend fun write(report: RemoteReport, patch: RawPatch, token: String): RemoteWriteOutcome
}
