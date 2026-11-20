package app.chrapak.detection

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YamnetLabelsTest {
    /** A fake model: some leading bytes (the flatbuffer) followed by a zip with the label entry. */
    private fun fakeModel(labels: List<String>?, prefix: ByteArray = ByteArray(64) { it.toByte() }): ByteArray {
        if (labels == null) return prefix
        val out = ByteArrayOutputStream()
        out.write(prefix) // like the real file: flatbuffer first, zip appended after it
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(YamnetLabels.LABEL_ENTRY))
        zip.write(labels.joinToString("\n").toByteArray())
        zip.closeEntry()
        zip.close()
        return out.toByteArray()
    }

    @Test
    fun `reads the label list and finds Snoring`() {
        val labels = listOf("Speech", "Breathing", "Wheeze", "Snoring", "Gasp")
        val read = YamnetLabels.read(fakeModel(labels))
        assertEquals(labels, read)
        assertEquals(3, YamnetLabels.indexOf(read!!, "Snoring"))
    }

    @Test
    fun `label lookup ignores case and reports a missing label as null`() {
        val labels = listOf("Speech", "Snoring")
        assertEquals(1, YamnetLabels.indexOf(labels, "snoring"))
        assertNull(YamnetLabels.indexOf(labels, "Whistle"))
    }

    @Test
    fun `a file without a label list gives null instead of crashing`() {
        assertNull(YamnetLabels.read(fakeModel(null)))
        assertNull(YamnetLabels.read(ByteArray(0)))
    }
}
