package app.chrapak.health

import androidx.health.connect.client.records.SleepSessionRecord

/** Maps Health Connect stage codes to the four stages the report shows; unknown stages are skipped. */
fun mapStage(type: Int): SleepStageType? = when (type) {
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    -> SleepStageType.AWAKE
    SleepSessionRecord.STAGE_TYPE_LIGHT,
    SleepSessionRecord.STAGE_TYPE_SLEEPING,
    -> SleepStageType.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
    else -> null
}
