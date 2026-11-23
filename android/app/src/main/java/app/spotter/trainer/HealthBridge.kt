package app.spotter.trainer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass

/** Reads recovery data the Pixel Watch syncs into Health Connect, and writes Spotter workouts back. */
class HealthBridge(private val context: Context) {

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context, PROVIDER) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun status(): JSONObject {
        val sdk = HealthConnectClient.getSdkStatus(context, PROVIDER)
        val out = JSONObject()
            .put("available", sdk == HealthConnectClient.SDK_AVAILABLE)
            .put("needsUpdate", sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
        val granted = if (sdk == HealthConnectClient.SDK_AVAILABLE)
            client.permissionController.getGrantedPermissions() else emptySet()
        return out.put("granted", JSONArray(granted.toList()))
    }

    private suspend fun <T : Record> read(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val records = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(type, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token)
            )
            records += response.records
            token = response.pageToken
        } while (token != null && records.size < 5000)
        return records
    }

    private suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun readiness(): JSONObject {
        val perms = granted()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val out = JSONObject()

        // Last night's sleep: sessions that ended in the last 18 hours
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in perms) {
            val sessions = read(SleepSessionRecord::class, now.minus(Duration.ofHours(36)), now)
                .filter { it.endTime.isAfter(now.minus(Duration.ofHours(18))) }
            if (sessions.isNotEmpty()) {
                var total = 0L; var deep = 0L; var rem = 0L; var awake = 0L; var hasStages = false
                sessions.forEach { s ->
                    total += Duration.between(s.startTime, s.endTime).toMinutes()
                    s.stages.forEach { st ->
                        hasStages = true
                        val m = Duration.between(st.startTime, st.endTime).toMinutes()
                        when (st.stage) {
                            SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
                            SleepSessionRecord.STAGE_TYPE_REM -> rem += m
                            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += m
                        }
                    }
                }
                val sleep = JSONObject()
                    .put("minutes", total - awake)
                    .put("start", sessions.minOf { it.startTime }.toString())
                    .put("end", sessions.maxOf { it.endTime }.toString())
                if (hasStages) sleep.put("deepMin", deep).put("remMin", rem).put("awakeMin", awake)
                out.put("sleep", sleep)
            }
        }

        // Resting heart rate: latest vs the previous week's average
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in perms) {
            val rhr = read(RestingHeartRateRecord::class, now.minus(Duration.ofDays(14)), now).sortedBy { it.time }
            if (rhr.isNotEmpty()) {
                val latest = rhr.last()
                val prior = rhr.filter { it.time.isBefore(latest.time.minus(Duration.ofHours(12))) && it.time.isAfter(latest.time.minus(Duration.ofDays(8))) }
                val o = JSONObject().put("latest", latest.beatsPerMinute).put("at", latest.time.toString())
                if (prior.size >= 3) o.put("baseline", Math.round(prior.map { it.beatsPerMinute }.average()))
                out.put("restingHr", o)
            }
        }

        // Heart rate variability (RMSSD): latest vs the previous week's average
        if (HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in perms) {
            val hrv = read(HeartRateVariabilityRmssdRecord::class, now.minus(Duration.ofDays(14)), now).sortedBy { it.time }
            if (hrv.isNotEmpty()) {
                val latest = hrv.last()
                val prior = hrv.filter { it.time.isBefore(latest.time.minus(Duration.ofHours(12))) && it.time.isAfter(latest.time.minus(Duration.ofDays(8))) }
                val o = JSONObject().put("latest", latest.heartRateVariabilityMillis).put("at", latest.time.toString())
                if (prior.size >= 3) o.put("baseline", prior.map { it.heartRateVariabilityMillis }.average())
                out.put("hrv", o)
            }
        }

        // Steps today and yesterday
        if (HealthPermission.getReadPermission(StepsRecord::class) in perms) {
            val today = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val yesterday = today.minus(Duration.ofDays(1))
            out.put("steps", JSONObject()
                .put("today", steps(today, now))
                .put("yesterday", steps(yesterday, today)))
        }
        return out
    }

    private suspend fun steps(start: Instant, end: Instant): Long =
        client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL] ?: 0L

    /** Average and peak heart rate the watch recorded during a workout. */
    suspend fun sessionHeartRate(startIso: String, endIso: String): JSONObject {
        val start = Instant.parse(startIso); val end = Instant.parse(endIso)
        val samples = read(HeartRateRecord::class, start, end).flatMap { it.samples }
            .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
        if (samples.isEmpty()) return JSONObject().put("samples", 0)
        return JSONObject()
            .put("samples", samples.size)
            .put("avg", samples.map { it.beatsPerMinute }.average())
            .put("max", samples.maxOf { it.beatsPerMinute })
    }

    /** Saves a finished Spotter workout as a strength-training session (shows in Fitbit and other apps). */
    suspend fun writeWorkout(startIso: String, endIso: String, title: String, notes: String): JSONObject {
        val start = Instant.parse(startIso)
        var end = Instant.parse(endIso)
        if (!end.isAfter(start)) end = start.plus(Duration.ofMinutes(1))
        val zone = ZoneId.systemDefault()
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = ZonedDateTime.ofInstant(start, zone).offset,
            endTime = end,
            endZoneOffset = ZonedDateTime.ofInstant(end, zone).offset,
            metadata = Metadata.manualEntry(),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = title.ifBlank { "Spotter workout" },
            notes = notes.ifBlank { null },
        )
        val ids = client.insertRecords(listOf(record)).recordIdsList
        return JSONObject().put("id", ids.firstOrNull())
    }

    /** Opens Health Connect's permission screen, or its Play Store page when it isn't installed. */
    fun openHealthConnect(activity: android.app.Activity) {
        val sdk = HealthConnectClient.getSdkStatus(context, PROVIDER)
        val intent = if (sdk == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
                .setPackage("com.android.vending")
        }
        runCatching { activity.startActivity(intent) }.onFailure {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER")))
        }
    }
}
