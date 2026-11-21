package dev.daymark.data.local

import dev.daymark.data.local.dao.LocalDataDeletionDao

/** Caller owns the write transaction. Failure rolls back every authoritative table together. */
internal suspend fun LocalDataDeletionDao.deleteAllLocalRows() {
    deletePlaceAddresses()
    deleteAnnualVacationPolicies()
    deleteAttendance(); deleteTimeOffRows(); deleteBalanceRows(); deleteReportBatches(); deleteDirtyRows()
    deleteBindings(); deletePlanVersions(); deleteTrackers(); deleteSettings()
}
