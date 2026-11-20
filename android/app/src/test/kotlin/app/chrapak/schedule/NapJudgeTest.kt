package app.chrapak.schedule

import app.chrapak.detection.NapEvidence
import org.junit.Assert.assertEquals
import org.junit.Test

class NapJudgeTest {
    private val min = 60_000L
    private fun evidence(awake: Float = 0f, snoring: Float = 0f, breathing: Float = 0f) = NapEvidence(awake, snoring, breathing)

    @Test
    fun `silence alone confirms nothing`() {
        val j = NapJudge()
        repeat(10) { assertEquals(NapStage.CHECKING, j.onWindow(evidence(), sinceStartMs = it * 15_000L)) }
    }

    @Test
    fun `snoring confirms at once`() {
        assertEquals(NapStage.CONFIRMED, NapJudge().onWindow(evidence(snoring = 0.6f), 30_000))
    }

    @Test
    fun `steady breathing confirms after a few windows, a single breath does not`() {
        val j = NapJudge()
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(breathing = 0.4f), 15_000))
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(breathing = 0.1f), 30_000))
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(breathing = 0.4f), 45_000))
        assertEquals(NapStage.CONFIRMED, j.onWindow(evidence(breathing = 0.4f), 60_000))
    }

    @Test
    fun `two windows of awake sounds call the guess off`() {
        val j = NapJudge()
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(awake = 0.7f), 15_000))
        assertEquals(NapStage.ABORTED, j.onWindow(evidence(awake = 0.5f), 30_000))
    }

    @Test
    fun `one awake sound is not enough`() {
        val j = NapJudge()
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(awake = 0.9f), 15_000))
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(), 30_000))
    }

    @Test
    fun `snoring under a television is not a sleeper in a quiet room`() {
        // Awake sounds are louder than the snoring-like sounds, so this must not confirm.
        val j = NapJudge()
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(awake = 0.6f, snoring = 0.4f), 15_000))
    }

    @Test
    fun `breathing in a room with awake sounds does not count`() {
        val j = NapJudge()
        repeat(2) { j.onWindow(evidence(awake = 0.45f, breathing = 0.5f), it * 15_000L) }
        assertEquals(NapStage.ABORTED, j.stage)
    }

    @Test
    fun `the guess is dropped when there is no sign of a sleeper in time`() {
        val j = NapJudge(maxCheckMs = 20 * min)
        assertEquals(NapStage.CHECKING, j.onWindow(evidence(), 19 * min))
        assertEquals(NapStage.ABORTED, j.onWindow(evidence(), 20 * min))
    }

    @Test
    fun `a decision is final`() {
        val j = NapJudge()
        j.onWindow(evidence(snoring = 0.9f), 15_000)
        // Later awake sounds do not undo a confirmed nap.
        repeat(5) { assertEquals(NapStage.CONFIRMED, j.onWindow(evidence(awake = 0.9f), 30_000 + it * 15_000L)) }
        val aborted = NapJudge()
        repeat(2) { aborted.onWindow(evidence(awake = 0.9f), it * 15_000L) }
        assertEquals(NapStage.ABORTED, aborted.onWindow(evidence(snoring = 0.9f), 60_000))
    }
}
