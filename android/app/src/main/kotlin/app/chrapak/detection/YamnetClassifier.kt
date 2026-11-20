package app.chrapak.detection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Second detection stage: Google's YAMNet (AudioSet, 521 classes) through LiteRT. Takes 0.975 s of
 * 16 kHz audio and returns the probability of the "Snoring" class. Runs on one CPU thread, and
 * only ever while the cheap gate has an episode open.
 *
 * The model file is not in the repository; `scripts/fetch-yamnet.sh` downloads it to the assets.
 */
class YamnetClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val snoringIndex: Int,
    private val awakeIndices: IntArray,
    private val breathingIndex: Int,
    private val logTopClasses: Boolean,
) : SnoreClassifier, AutoCloseable {
    override val windowSamples = WINDOW_SAMPLES

    private val inputRank = interpreter.getInputTensor(0).shape().size
    private val flatInput = FloatArray(WINDOW_SAMPLES)
    private val batchedInput = arrayOf(flatInput)
    private val outputRank = interpreter.getOutputTensor(0).shape().size
    private val flatOutput = FloatArray(labels.size)
    private val batchedOutput = arrayOf(flatOutput)

    override fun score(window: ShortArray): Float {
        infer(window)
        return flatOutput[snoringIndex]
    }

    override fun napEvidence(window: ShortArray): NapEvidence {
        infer(window)
        return NapEvidence(
            awake = awakeIndices.maxOfOrNull { flatOutput[it] } ?: 0f,
            snoring = flatOutput[snoringIndex],
            breathing = if (breathingIndex >= 0) flatOutput[breathingIndex] else 0f,
        )
    }

    /** All 521 class probabilities for one window, for the clip analysis screen. */
    fun probabilities(window: ShortArray): FloatArray {
        infer(window)
        return flatOutput.copyOf()
    }

    val classLabels: List<String> get() = labels
    val snoringClassIndex: Int get() = snoringIndex

    private fun infer(window: ShortArray) {
        require(window.size == WINDOW_SAMPLES) { "YAMNet needs $WINDOW_SAMPLES samples, got ${window.size}" }
        for (i in window.indices) flatInput[i] = window[i] / 32768f
        interpreter.run(if (inputRank == 1) flatInput else batchedInput, if (outputRank == 1) flatOutput else batchedOutput)
        if (logTopClasses) logTop()
    }

    private fun logTop() {
        val top = flatOutput.indices.sortedByDescending { flatOutput[it] }.take(3)
        Log.d(TAG, top.joinToString("  ") { "%s=%.2f".format(labels[it], flatOutput[it]) } + "  snoring=%.2f".format(flatOutput[snoringIndex]))
    }

    override fun close() = interpreter.close()

    companion object {
        const val WINDOW_SAMPLES = 15_600
        private const val ASSET = "yamnet.tflite"

        /** AudioSet classes that mean someone is awake and doing something near the phone. */
        private val AWAKE_LABELS = listOf(
            "Speech", "Conversation", "Narration, monologue", "Music", "Television", "Radio",
            "Typing", "Computer keyboard", "Walk, footsteps", "Cutlery, silverware",
            "Dishes, pots, and pans", "Door", "Telephone", "Vacuum cleaner", "Water tap, faucet",
        )
        private const val TAG = "ChrapakYamnet"

        /** Null when the model is missing or unusable; the pipeline then runs on the gate alone. */
        fun load(context: Context, logTopClasses: Boolean = false): YamnetClassifier? = try {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val labels = YamnetLabels.read(bytes) ?: error("model has no label list")
            val snoring = YamnetLabels.indexOf(labels, "Snoring") ?: error("no Snoring class in the model")
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            buffer.rewind()
            val interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(1))
            Log.i(TAG, "loaded: input ${interpreter.getInputTensor(0).shape().toList()}, output ${interpreter.getOutputTensor(0).shape().toList()}, Snoring=$snoring")
            val awake = AWAKE_LABELS.mapNotNull { YamnetLabels.indexOf(labels, it) }.toIntArray()
            YamnetClassifier(interpreter, labels, snoring, awake, YamnetLabels.indexOf(labels, "Breathing") ?: -1, logTopClasses)
        } catch (e: Exception) {
            Log.w(TAG, "classifier unavailable, using the gate alone: ${e.message}")
            null
        }
    }
}
