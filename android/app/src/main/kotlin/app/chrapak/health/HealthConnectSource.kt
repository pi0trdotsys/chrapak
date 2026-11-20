package app.chrapak.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

enum class HealthStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/** What one app (RingConn, Mi Fitness, ...) wrote to Health Connect in a time range. */
data class OriginSummary(
    val packageName: String,
    val sleepSessions: Int,
    val stageSegments: Int,
    val heartRateSamples: Int,
    val spo2Samples: Int,
) {
    /** Friendly name for the apps this project is built for; others show their package name. */
    val label: String
        get() = when {
            packageName.contains("ringconn", ignoreCase = true) -> "RingConn"
            packageName.contains("xiaomi", ignoreCase = true) || packageName.startsWith("com.mi.") -> "Xiaomi / Mi Fitness"
            else -> packageName
        }
}

/**
 * Read-only access to sleep, heart rate and SpO2 in Health Connect. Chrapak never writes health data.
 * The stage, heart rate and SpO2 values are mapped to our own types so the rest of the app does
 * not depend on the Health Connect library.
 */
class HealthConnectSource(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.UPDATE_REQUIRED
        else -> HealthStatus.UNAVAILABLE
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        status() == HealthStatus.AVAILABLE && client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Sleep stages, SpO2 and heart rate between the two times, or an empty result without access. */
    suspend fun readNight(fromMs: Long, toMs: Long): NightHealth {
        if (!hasPermissions()) return NightHealth()
        val from = Instant.ofEpochMilli(fromMs)
        val to = Instant.ofEpochMilli(toMs)
        val stages = readAll<SleepSessionRecord>(from, to)
            .flatMap { it.stages }
            .mapNotNull { stage ->
                mapStage(stage.stage)?.let { StageSegment(stage.startTime.toEpochMilli(), stage.endTime.toEpochMilli(), it) }
            }
            .sortedBy { it.startMs }
        val spo2 = readAll<OxygenSaturationRecord>(from, to)
            .map { VitalPoint(it.time.toEpochMilli(), it.percentage.value, it.metadata.dataOrigin.packageName) }
            .sortedBy { it.tsMs }
        val heartRate = readAll<HeartRateRecord>(from, to)
            .flatMap { record ->
                record.samples.map { VitalPoint(it.time.toEpochMilli(), it.beatsPerMinute.toDouble(), record.metadata.dataOrigin.packageName) }
            }
            .sortedBy { it.tsMs }
        return NightHealth(stages, spo2, heartRate)
    }

    /** Per-app counts of what Health Connect holds in the range: answers "does my ring write here?". */
    suspend fun summarize(fromMs: Long, toMs: Long): List<OriginSummary> {
        if (!hasPermissions()) return emptyList()
        val from = Instant.ofEpochMilli(fromMs)
        val to = Instant.ofEpochMilli(toMs)
        val sleep = readAll<SleepSessionRecord>(from, to).groupBy { it.metadata.dataOrigin.packageName }
        val heart = readAll<HeartRateRecord>(from, to).groupBy { it.metadata.dataOrigin.packageName }
        val oxygen = readAll<OxygenSaturationRecord>(from, to).groupBy { it.metadata.dataOrigin.packageName }
        return (sleep.keys + heart.keys + oxygen.keys).sorted().map { pkg ->
            OriginSummary(
                packageName = pkg,
                sleepSessions = sleep[pkg].orEmpty().size,
                stageSegments = sleep[pkg].orEmpty().sumOf { it.stages.size },
                heartRateSamples = heart[pkg].orEmpty().sumOf { it.samples.size },
                spo2Samples = oxygen[pkg].orEmpty().size,
            )
        }
    }

    private suspend inline fun <reified T : Record> readAll(from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(T::class, TimeRangeFilter.between(from, to), pageSize = 5_000, pageToken = token),
            )
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }
}
