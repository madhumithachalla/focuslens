package com.focuslens

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * Optional bridge to Android's Health Connect — the system-level store that a watch or
 * health app (Samsung Health, Fitbit, Flo, Clue, and others) can write cycle data into.
 * Nothing here runs automatically: the user explicitly taps "Import from watch" on the
 * Cycle screen, grants the permission prompt Android itself shows, and this reads whatever
 * period-start dates are already there — it never writes anything back, and never reads
 * anything beyond that one record type.
 *
 * Requires Health Connect to be present (built into the OS on Android 14+, a separate Play
 * Store app before that) and a source app that actually writes menstruation data into it —
 * not every watch or fitness app does, so an empty result here is expected, not a bug.
 */
object HealthConnectBridge {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(MenstruationPeriodRecord::class))

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun needsInstall(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun hasPermission(context: Context): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        return client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    fun requestPermissionContract() = PermissionController.createRequestPermissionResultContract()

    /** Distinct period-start dates (yyyyMMdd) from every MenstruationPeriodRecord in the last [days] days. */
    suspend fun readPeriodStarts(context: Context, days: Int = 365): List<String> {
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = MenstruationPeriodRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return response.records.map { fmt.format(Date.from(it.startTime)) }.distinct().sorted()
    }
}
