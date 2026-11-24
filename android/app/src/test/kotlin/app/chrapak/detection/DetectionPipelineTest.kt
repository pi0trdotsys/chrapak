package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPipelineTest {
    private val rate = TestSignals.RATE

    /** Ten seconds of quiet, three snore bursts with short pauses (6.5 s episode), then quiet. */
    private fun night(): ShortArray = TestSignals.concat(
        TestSignals.silence(10.0, seed = 1),
        TestSignals.snore(1.5), TestSignals.silence(1.0, seed = 3),
        TestSignals.snore(1.5), TestSignals.silence(1.0, seed = 4),
        TestSignals.snore(1.5),
        TestSignals.silence(6.0, seed = 5),
    )

    private fun run(classifier: SnoreClassifier?, signal: ShortArray = night()): Pair<List<DetectedEvent>, DetectionPipeline> {
        val events = mutableListOf<DetectedEvent>()
        val pipeline = DetectionPipeline(DetectionConfig(), classifier, startEpochMs = 1_000_000L) { events += it }
        // Feed in uneven chunks, like a real microphone read.
        var pos = 0
        while (pos < signal.size) {
            val len = minOf(7_777, signal.size - pos)
            pipeline.feed(signal.copyOfRange(pos, pos + len))
            pos += len
        }
        pipeline.finish()
        return events to pipeline
    }

    private fun classifier(score: Float) = object : SnoreClassifier {
        override val windowSamples = 15_600
        override fun score(window: ShortArray): Float {
            assertEquals(15_600, window.size)
            return score
        }
    }

    @Test
    fun `one merged episode is found at the right time`() {
        val (events, _) = run(classifier = null)
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(1_010_000.0, event.startMs.toDouble(), 150.0)
        assertEquals(6_500.0, event.durationMs.toDouble(), 300.0)
        assertEquals(GATE_ONLY_CONFIDENCE, event.confidence, 0f)
    }

    @Test
    fun `clip contains pre roll before the event`() {
        val (events, _) = run(classifier = null)
        val event = events[0]
        val preRollMs = event.startMs - event.clipStartMs
        assertEquals(5_000.0, preRollMs.toDouble(), 200.0)
        assertTrue(event.pcm.size > rate * 11) // 5 s pre-roll + 6.5 s event + trailing gap
    }

    @Test
    fun `a pure quiet night yields nothing and never runs the classifier`() {
        val (events, pipeline) = run(classifier(0.9f), TestSignals.silence(60.0))
        assertEquals(0, events.size)
        assertEquals(0, pipeline.classifierCalls)
    }

    @Test
    fun `classifier only runs while an episode is open`() {
        val (events, pipeline) = run(classifier(0.9f))
        assertEquals(1, events.size)
        assertEquals(0.9f, events[0].confidence, 0.0001f)
        // About one call per second while the episode is open (6.5 s of snoring plus the 6 s merge
        // gap before it closes), and none during the 10 s of quiet before it.
        assertTrue("calls: ${pipeline.classifierCalls}", pipeline.classifierCalls in 3..14)
    }

    @Test
    fun `classifier can lower confidence but not remove the episode`() {
        val (events, pipeline) = run(classifier(0.1f))
        // Episode still exists even with low confidence (below MIN_CONFIDENCE = 0.3)
        assertEquals(1, events.size)
        assertEquals(0.1f, events[0].confidence, 0.0001f)
        assertTrue("audio PCM should be present", events[0].pcm.isNotEmpty())
        assertTrue(pipeline.classifierCalls > 0)
    }

    @Test
    fun `gate-only events get the fixed gate confidence`() {
        val (events, _) = run(classifier = null)
        assertEquals(1, events.size)
        assertEquals(GATE_ONLY_CONFIDENCE, events[0].confidence, 0f)
        assertTrue(events[0].pcm.isNotEmpty())
    }

    @Test
    fun `sparse clicks over many seconds are not an episode`() {
        // A 0.1 s loud tick every 3 s for 30 s: lots of span, hardly any active time.
        val tick = TestSignals.snore(0.1)
        val gap = TestSignals.silence(2.9, seed = 9)
        val parts = (0 until 10).flatMap { listOf(tick, gap) }
        val (events, _) = run(classifier = null, signal = TestSignals.concat(TestSignals.silence(3.0), *parts.toTypedArray(), TestSignals.silence(8.0)))
        assertEquals(0, events.size)
    }

    @Test
    fun `finish emits an episode still open when listening stops`() {
        val cut = TestSignals.concat(TestSignals.silence(10.0), TestSignals.snore(5.0))
        val (events, _) = run(classifier = null, signal = cut)
        assertEquals(1, events.size)
        assertEquals(5_000.0, events[0].durationMs.toDouble(), 200.0)
    }

    @Test
    fun `a rhythmic episode is confident even when the model is not`() {
        // Three bursts about 2.5 s apart: a breath cycle, even though the classifier scores low.
        val (events, _) = run(classifier(0.1f))
        assertEquals(1, events.size)
        val event = events[0]
        assertTrue("rhythm: ${event.rhythmScore}", event.rhythmScore >= DetectionQuality.RHYTHM_CONFIDENT)
        assertTrue(event.isConfident)
        assertEquals(0.1f, event.classifierScore!!, 0.0001f)
        // The stored confidence is left alone: it stays the model's own number.
        assertEquals(0.1f, event.confidence, 0.0001f)
    }

    @Test
    fun `gate-only episodes report no classifier score but still carry the rhythm`() {
        val (events, _) = run(classifier = null)
        val event = events[0]
        assertNull(event.classifierScore)
        assertTrue("rhythm: ${event.rhythmScore}", event.rhythmScore > 0f)
        assertTrue(event.isConfident)
        assertEquals(GATE_ONLY_CONFIDENCE, event.confidence, 0f)
    }

    @Test
    fun `pre roll buffer wraps and returns the latest samples oldest first`() {
        val buffer = PreRollBuffer(5)
        buffer.write(shortArrayOf(1, 2, 3))
        buffer.write(shortArrayOf(4, 5, 6, 7))
        assertEquals(listOf<Short>(3, 4, 5, 6, 7), buffer.latest().toList())
        assertEquals(listOf<Short>(6, 7), buffer.latest(2).toList())
        buffer.write(shortArrayOf(9, 9, 9, 9, 9, 9, 8))
        assertEquals(listOf<Short>(9, 9, 9, 9, 8), buffer.latest().toList())
    }
}
