package dev.daymark.di.ktor

import dev.daymark.data.local.DatabaseTransactions
import dev.daymark.data.local.DaymarkDatabase
import dev.daymark.data.local.RoomDatabaseTransactions
import dev.daymark.data.local.dao.AttendanceDao
import dev.daymark.data.local.dao.DirtyProjectionDao
import dev.daymark.data.local.dao.LocalDataDeletionDao
import dev.daymark.data.local.dao.PlaceAddressDao
import dev.daymark.data.local.dao.PlanningDao
import dev.daymark.data.local.dao.ProfileDao
import dev.daymark.data.local.dao.ReportDao
import org.koin.dsl.module

internal fun databaseModule() = module {
    single<ProfileDao> { get<DaymarkDatabase>().profileDao() }
    single<AttendanceDao> { get<DaymarkDatabase>().attendanceDao() }
    single<PlanningDao> { get<DaymarkDatabase>().planningDao() }
    single<DirtyProjectionDao> { get<DaymarkDatabase>().dirtyProjectionDao() }
    single<ReportDao> { get<DaymarkDatabase>().reportDao() }
    single<PlaceAddressDao> { get<DaymarkDatabase>().placeAddressDao() }
    single<LocalDataDeletionDao> { get<DaymarkDatabase>().localDataDeletionDao() }
    single<DatabaseTransactions> { RoomDatabaseTransactions(get()) }
}
