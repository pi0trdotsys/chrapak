package app.chrapak.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SnoreSegmenterTest {
    private val frameMs = 100

    /** Feeds a pattern like "AAAA....AA" where A is an active frame and . is silence. */
    private fun run(segmenter: SnoreSegmenter, pattern: String): List<SnoreSegment> {
        val out = mutableListOf<SnoreSegment>()
        pattern.forEachIndexed { i, c ->
            segmenter.onFrame(i * frameMs.toLong(), frameMs, c == 'A', -30f)?.let(out::add)
        }
        segmenter.flush()?.let(out::add)
        return out
    }

    @Test
    fun `pauses shorter than the merge gap keep one episode`() {
        val segmenter = SnoreSegmenter(mergeGapMs = 2_000, minEventMs = 3_000)
        val segments = run(segmenter, "A".repeat(15) + ".".repeat(10) + "A".repeat(15) + ".".repeat(30))
        assertEquals(1, segments.size)
        assertEquals(0L, segments[0].startMs)
        assertEquals(4_000L, segments[0].endMs)
    }

    @Test
    fun `a long pause splits episodes`() {
        val segmenter = SnoreSegmenter(mergeGapMs = 2_000, minEventMs = 3_000)
        val segments = run(segmenter, "A".repeat(40) + ".".repeat(30) + "A".repeat(40) + ".".repeat(30))
        assertEquals(2, segments.size)
    }

    @Test
    fun `episodes shorter than the minimum are dropped`() {
        val segmenter = SnoreSegmenter(mergeGapMs = 2_000, minEventMs = 3_000)
        assertEquals(0, run(segmenter, "A".repeat(10) + ".".repeat(40)).size)
    }

    @Test
    fun `flush emits an open episode`() {
        val segmenter = SnoreSegmenter(mergeGapMs = 2_000, minEventMs = 3_000)
        run(segmenter, "").let { assertEquals(0, it.size) }
        "A".repeat(50).forEachIndexed { i, _ -> segmenter.onFrame(i * frameMs.toLong(), frameMs, true, -20f) }
        assertNotNull(segmenter.flush())
        assertNull(segmenter.flush())
    }

    @Test
    fun `peak and average levels are reported`() {
        val segmenter = SnoreSegmenter(mergeGapMs = 2_000, minEventMs = 100)
        segmenter.onFrame(0, frameMs, true, -40f)
        segmenter.onFrame(100, frameMs, true, -20f)
        val segment = segmenter.flush()!!
        assertEquals(-20f, segment.peakDb, 0.001f)
        assertEquals(-30f, segment.avgDb, 0.001f)
    }
}
