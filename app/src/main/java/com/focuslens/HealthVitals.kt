package com.focuslens

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Read-only access to real heart-rate, HRV and sleep data that a watch app has written to
 * Health Connect. Nothing is simulated or estimated beyond the clearly labelled wellness
 * estimate below, and nothing is written back. Not every watch app writes HRV or sleep
 * stages, so every result can legitimately be empty.
 */
object HealthVitals {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun needsInstall(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    suspend fun hasPermission(context: Context): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        return client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    // ---- Data holders ----

    data class HrSample(val time: Instant, val bpm: Long)

    data class HrSummary(val min: Long, val avg: Long, val max: Long, val count: Int)

    data class HrvPoint(val time: Instant, val rmssdMs: Double)

    data class HrvSummary(
        val latest: HrvPoint?,
        val sevenDayAvgMs: Double?,
        val sevenDayCount: Int
    )

    enum class StressLevel(val label: String) {
        RELAXED("Relaxed"),
        NORMAL("Normal"),
        ELEVATED("Elevated"),
        HIGH_STRAIN("High strain")
    }

    data class StressEstimate(
        val level: StressLevel?,
        val ratio: Double?,
        val todayRmssdMs: Double?,
        val baselineMs: Double?,
        val baselineDays: Int,
        val message: String
    )

    data class StageMinutes(
        val awake: Long,
        val light: Long,
        val deep: Long,
        val rem: Long,
        val other: Long
    ) {
        val total: Long get() = awake + light + deep + rem + other
    }

    data class SleepNight(
        val start: Instant,
        val end: Instant,
        val totalMinutes: Long,
        val stagesReported: Boolean,
        val stages: StageMinutes,
        val isNap: Boolean
    ) {
        fun percent(minutes: Long): Int =
            if (stages.total <= 0L) 0 else Math.round(minutes * 100.0 / stages.total).toInt()
    }

    data class Snapshot(
        val hrSamples: List<HrSample>,
        val hrSummary: HrSummary?,
        val hrv: HrvSummary,
        val stress: StressEstimate,
        val sleepSessions: List<SleepNight>
    ) {
        val isEmpty: Boolean
            get() = hrSamples.isEmpty() && hrv.latest == null && sleepSessions.isEmpty()
    }

    // ---- Reading ----

    /** Reads everything in one go. Call from a coroutine; it does its own paging. */
    suspend fun readSnapshot(context: Context): Snapshot {
        val hr = readHeartRate24h(context)
        val hrvPoints = readHrv(context, 14)
        val sleep = readSleep(context, 14)
        return Snapshot(
            hrSamples = hr,
            hrSummary = summarizeHeartRate(hr),
            hrv = summarizeHrv(hrvPoints),
            stress = estimateStress(hrvPoints),
            sleepSessions = sleep
        )
    }

    suspend fun readHeartRate24h(context: Context): List<HrSample> {
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val start = end.minus(24, ChronoUnit.HOURS)
        val out = ArrayList<HrSample>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            for (record in response.records) {
                for (s in record.samples) {
                    if (!s.time.isBefore(start) && !s.time.isAfter(end)) {
                        out.add(HrSample(s.time, s.beatsPerMinute))
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null && out.size < 50_000)
        return out.sortedBy { it.time }
    }

    fun summarizeHeartRate(samples: List<HrSample>): HrSummary? {
        if (samples.isEmpty()) return null
        val bpms = samples.map { it.bpm }
        return HrSummary(
            min = bpms.minOrNull() ?: 0L,
            avg = Math.round(bpms.average()),
            max = bpms.maxOrNull() ?: 0L,
            count = bpms.size
        )
    }

    suspend fun readHrv(context: Context, days: Int): List<HrvPoint> {
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val out = ArrayList<HrvPoint>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            for (r in response.records) {
                out.add(HrvPoint(r.time, r.heartRateVariabilityMillis))
            }
            pageToken = response.pageToken
        } while (pageToken != null && out.size < 20_000)
        return out.sortedBy { it.time }
    }

    fun summarizeHrv(points: List<HrvPoint>): HrvSummary {
        val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val week = points.filter { it.time.isAfter(weekAgo) }
        return HrvSummary(
            latest = points.maxByOrNull { it.time },
            sevenDayAvgMs = if (week.isEmpty()) null else week.map { it.rmssdMs }.average(),
            sevenDayCount = week.size
        )
    }

    /**
     * Personal-baseline stress estimate: today's average RMSSD divided by the median of the
     * daily averages of the previous days in the window. Needs at least 5 prior days.
     */
    fun estimateStress(points: List<HrvPoint>): StressEstimate {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val byDay = points.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { entry -> entry.value.map { it.rmssdMs }.average() }
        val priorDays = byDay.filterKeys { it.isBefore(today) }.values.toList()
        val todayValue = byDay[today]

        if (priorDays.size < 5) {
            return StressEstimate(
                level = null, ratio = null, todayRmssdMs = todayValue, baselineMs = null,
                baselineDays = priorDays.size,
                message = "Not enough history yet: a personal baseline needs HRV on at least 5 " +
                    "different days (you have ${priorDays.size}). Wear your watch and check back."
            )
        }
        val baseline = median(priorDays)
        if (todayValue == null) {
            return StressEstimate(
                level = null, ratio = null, todayRmssdMs = null, baselineMs = baseline,
                baselineDays = priorDays.size,
                message = "No HRV reading from today yet, so there is nothing to compare to your baseline."
            )
        }
        if (baseline <= 0.0) {
            return StressEstimate(
                level = null, ratio = null, todayRmssdMs = todayValue, baselineMs = baseline,
                baselineDays = priorDays.size,
                message = "Your baseline HRV is zero, which looks like bad data from the source app."
            )
        }
        val ratio = todayValue / baseline
        val level = when {
            ratio >= 0.9 -> StressLevel.RELAXED
            ratio >= 0.7 -> StressLevel.NORMAL
            ratio >= 0.5 -> StressLevel.ELEVATED
            else -> StressLevel.HIGH_STRAIN
        }
        return StressEstimate(
            level = level, ratio = ratio, todayRmssdMs = todayValue, baselineMs = baseline,
            baselineDays = priorDays.size,
            message = "Today's HRV is ${Math.round(ratio * 100)}% of your own typical level " +
                "(median of ${priorDays.size} earlier days)."
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    suspend fun readSleep(context: Context, days: Int): List<SleepNight> {
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val records = ArrayList<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            records.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null && records.size < 2_000)
        val zone = ZoneId.systemDefault()
        return records.map { toNight(it, zone) }.sortedByDescending { it.start }
    }

    private fun toNight(r: SleepSessionRecord, zone: ZoneId): SleepNight {
        var awake = 0L
        var light = 0L
        var deep = 0L
        var rem = 0L
        var other = 0L
        for (st in r.stages) {
            val mins = Duration.between(st.startTime, st.endTime).toMinutes()
            when (st.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += mins
                SleepSessionRecord.STAGE_TYPE_LIGHT -> light += mins
                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += mins
                SleepSessionRecord.STAGE_TYPE_REM -> rem += mins
                else -> other += mins // SLEEPING (unspecified depth), UNKNOWN, etc.
            }
        }
        val total = Duration.between(r.startTime, r.endTime).toMinutes()
        // Stages count as "reported" only when they distinguish depth. A lone generic
        // SLEEPING stage tells us nothing about light/deep/REM.
        val reported = (light + deep + rem) > 0L
        val startHour = r.startTime.atZone(zone).hour
        val isNap = total < 180L && startHour >= 9 && startHour < 20
        return SleepNight(
            start = r.startTime,
            end = r.endTime,
            totalMinutes = total,
            stagesReported = reported,
            stages = StageMinutes(awake, light, deep, rem, other),
            isNap = isNap
        )
    }
}
