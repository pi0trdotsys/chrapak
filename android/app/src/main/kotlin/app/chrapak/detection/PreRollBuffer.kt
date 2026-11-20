package app.chrapak.detection

/** Fixed-size ring of the most recent samples, kept in memory so a clip can start before the trigger. */
class PreRollBuffer(val capacity: Int) {
    private val buffer = ShortArray(capacity)
    private var writePos = 0

    var size = 0
        private set

    fun write(src: ShortArray, offset: Int = 0, length: Int = src.size - offset) {
        val start = if (length > capacity) offset + length - capacity else offset
        val count = minOf(length, capacity)
        for (i in 0 until count) {
            buffer[writePos] = src[start + i]
            writePos = if (writePos + 1 == capacity) 0 else writePos + 1
        }
        size = minOf(capacity, size + count)
    }

    /** The latest [maxSamples] samples (or fewer if not that many yet), oldest first. */
    fun latest(maxSamples: Int = size): ShortArray {
        val count = minOf(maxSamples, size)
        val out = ShortArray(count)
        var pos = (writePos - count + capacity) % capacity
        for (i in 0 until count) {
            out[i] = buffer[pos]
            pos = if (pos + 1 == capacity) 0 else pos + 1
        }
        return out
    }
}
