package dev.daymark.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    @Test
    fun storage_uses_focused_daos_without_an_aggregate_facade() {
        val sources = Path.of("src/commonMain/kotlin/dev/daymark")
        val daoDirectory = sources.resolve("data/local/dao")
        val expected = setOf(
            "ProfileDao", "AttendanceDao", "PlanningDao", "DirtyProjectionDao",
            "ReportDao", "PlaceAddressDao", "LocalDataDeletionDao"
        )
        assertTrue(Files.isDirectory(daoDirectory), "Focused DAO directory must exist")
        Files.list(daoDirectory).use { paths ->
            val interfaces = paths.filter { it.toString().endsWith(".kt") }
                .map { it.fileName.toString().removeSuffix(".kt") }.toList().toSet()
            assertTrue(interfaces == expected, "Keep the seven agreed DAO boundaries")
        }
        Files.walk(sources).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { path ->
                val source = Files.readString(path)
                assertTrue(!source.contains("DaymarkDao"), "Aggregate DAO dependency in $path")
                if (path.startsWith(daoDirectory)) {
                    val name = path.fileName.toString().removeSuffix(".kt")
                    assertTrue(
                        Regex("interface\\s+$name\\s*\\{").containsMatchIn(source),
                        "Focused DAOs must not inherit an aggregate interface in $path"
                    )
                    assertTrue(
                        expected.filter { it != name }.none(source::contains),
                        "A DAO must not aggregate other DAOs in $path"
                    )
                } else if (path.fileName.toString() != "DaymarkDatabase.kt" &&
                    path != sources.resolve("di/ktor/DatabaseModule.kt")
                ) {
                    assertTrue(
                        !source.contains("@Dao"),
                        "Room DAO outside agreed boundaries in $path"
                    )
                    assertTrue(
                        !expected.all(source::contains),
                        "Aggregate DAO replacement in $path"
                    )
                }
            }
        }
    }

    @Test
    fun shared_consumers_depend_on_daos_and_transaction_contracts() {
        val sources = Path.of("src/commonMain/kotlin/dev/daymark")
        val databaseOwners = setOf("DaymarkDatabase.kt", "RoomDatabaseTransactions.kt")
        Files.walk(sources).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }
                .filter { !it.startsWith(sources.resolve("di")) }
                .filter { it.fileName.toString() !in databaseOwners }
                .forEach { path ->
                    assertTrue(
                        !Files.readString(path).contains("DaymarkDatabase"),
                        "Database dependency outside storage infrastructure in $path"
                    )
                }
        }
    }

    @Test
    fun presentation_uses_repository_interfaces_instead_of_storage_or_app_graph() {
        val sources = Path.of("src/commonMain/kotlin/dev/daymark")
        val presentation = sources.resolve("presentation")
        assertTrue(Files.isDirectory(presentation), "Feature presentation packages must exist")
        Files.walk(presentation).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.forEach { path ->
                val text = Files.readString(path)
                val forbidden = listOf(
                    "androidx.room",
                    "androidx.sqlite",
                    "dev.daymark.data.local",
                    "AppGraph",
                    "ReportingRuntime"
                )
                assertTrue(
                    forbidden.none(text::contains),
                    "Storage/composition dependency in $path"
                )
            }
        }
    }
}
