package dev.daymark.reporting

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

/** Single-dispatch transport: retry/journaling policy belongs to the publisher, never the HTTP client. */
class GoogleReportTransport(private val client: HttpClient) {
    suspend fun accountReference(token: String): String = sanitizedRead {
        val response = client.get("https://www.googleapis.com/drive/v3/about") {
            bearerAuth(token); parameter("fields", "user(permissionId)")
        }
        response.checkedJson().getValue("user").jsonObject.getValue("permissionId")
            .jsonPrimitive.content.takeIf { it.isNotBlank() } ?: throw ReportAccessException("invalid_response")
    }

    suspend fun discover(trackerId: String, token: String): ReportDiscovery = sanitizedRead {
        require(trackerId.isNotBlank())
        val reports = mutableListOf<RemoteReport>()
        val seenPages = mutableSetOf<String>()
        var page: String? = null
        do {
            val response = client.get("https://www.googleapis.com/drive/v3/files") {
                bearerAuth(token)
                parameter("q", "trashed = false and mimeType = 'application/vnd.google-apps.spreadsheet' and appProperties has { key='daymarkTrackerId' and value='${driveLiteral(trackerId)}' }")
                parameter("fields", "nextPageToken,files(id,appProperties)")
                parameter("pageSize", "100")
                page?.let { parameter("pageToken", it) }
            }
            val body = response.checkedJson()
            for (file in body.getValue("files").jsonArray) {
                val value = file.jsonObject
                reports += RemoteReport(value.getValue("id").jsonPrimitive.content, emptyMap(),
                    value["appProperties"]?.jsonObject?.get("daymarkSchemaVersion")?.jsonPrimitive?.intOrNull)
            }
            page = body["nextPageToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            if (page != null && !seenPages.add(page)) throw ReportAccessException("invalid_response")
        } while (page != null)
        val unique = reports.distinctBy { it.id }
        when (unique.size) {
            0 -> ReportDiscovery.None
            1 -> ReportDiscovery.Found(unique.single())
            else -> ReportDiscovery.Multiple(unique)
        }
    }

    suspend fun create(title: String, trackerId: String, token: String): ReportCreation {
        require(title.isNotBlank() && trackerId.isNotBlank())
        return try {
            val response = client.post("https://www.googleapis.com/drive/v3/files") {
                bearerAuth(token); parameter("fields", "id")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("name", title)
                    put("mimeType", "application/vnd.google-apps.spreadsheet")
                    putJsonObject("appProperties") { put("daymarkTrackerId", trackerId) }
                }.toString())
            }
            when {
                response.status.value in 200..299 -> {
                    val id = response.checkedJson().getValue("id").jsonPrimitive.content
                    if (id.isBlank()) ReportCreation.Ambiguous
                    else ReportCreation.Ready(RemoteReport(id, emptyMap(), null))
                }
                response.status.value == 408 || response.status.value >= 500 -> ReportCreation.Ambiguous
                else -> ReportCreation.Unavailable
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ReportCreation.Ambiguous }
    }

    suspend fun read(report: RemoteReport, ranges: List<String>, token: String): RemoteSnapshot = sanitizedRead {
        validateId(report.id)
        if (ranges.isEmpty()) return@sanitizedRead RemoteSnapshot(emptyMap())
        val response = client.get("https://sheets.googleapis.com/v4/spreadsheets/${report.id}/values:batchGet") {
            bearerAuth(token)
            ranges.forEach { parameter("ranges", it) }
            parameter("valueRenderOption", "UNFORMATTED_VALUE")
            parameter("dateTimeRenderOption", "SERIAL_NUMBER")
        }
        val values = response.checkedJson().getValue("valueRanges").jsonArray
        if (values.size != ranges.size) throw ReportAccessException("invalid_response")
        RemoteSnapshot(ranges.zip(values).associate { (requested, value) ->
            requested to (value.jsonObject["values"]?.jsonArray?.map { row ->
                row.jsonArray.map { it.jsonPrimitive }
            } ?: emptyList())
        })
    }

    suspend fun write(report: RemoteReport, patch: RawPatch, token: String): RemoteWriteOutcome {
        validateId(report.id)
        if (patch.ranges.isEmpty()) return RemoteWriteOutcome.Acknowledged
        return try {
            val response = client.post("https://sheets.googleapis.com/v4/spreadsheets/${report.id}/values:batchUpdate") {
                bearerAuth(token); contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("valueInputOption", "RAW")
                    put("includeValuesInResponse", false)
                    putJsonArray("data") {
                        patch.ranges.forEach { range ->
                            addJsonObject {
                                put("range", range.a1); put("majorDimension", "ROWS")
                                put("values", JsonArray(range.cells.map { JsonArray(it) }))
                            }
                        }
                    }
                }.toString())
            }
            when (response.status.value) {
                in 200..299 -> RemoteWriteOutcome.Acknowledged
                401 -> RemoteWriteOutcome.AuthorizationRequired
                400, 409 -> RemoteWriteOutcome.SchemaConflict
                404, 410 -> RemoteWriteOutcome.ReportUnavailable
                429 -> RemoteWriteOutcome.Retryable(response.headers[HttpHeaders.RetryAfter]
                    ?.toLongOrNull()?.takeIf { it >= 0 }?.seconds)
                408, in 500..599 -> RemoteWriteOutcome.Ambiguous
                else -> RemoteWriteOutcome.ReportUnavailable
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { RemoteWriteOutcome.Ambiguous }
    }

    private suspend fun HttpResponse.checkedJson(): JsonObject {
        if (status.value !in 200..299) throw ReportAccessException(when (status.value) {
            401 -> "authorization_required"
            404, 410 -> "report_unavailable"
            else -> "report_read_failed"
        })
        return Json.parseToJsonElement(bodyAsText()).jsonObject
    }
    private suspend fun <T> sanitizedRead(block: suspend () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (known: ReportAccessException) { throw known }
    catch (_: Exception) { throw ReportAccessException("report_read_failed") }

    private fun validateId(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid report ID" } }
    private fun driveLiteral(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}
