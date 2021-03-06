package core

import org.tensorflow.SavedModelBundle
import org.tensorflow.Session
import org.tensorflow.Tensor
import java.io.File
import java.nio.FloatBuffer
import java.util.*

/**
 * The object that interfaces with the model created in TensorFlow
 * @see org.tensorflow.Session
 * @see org.tensorflow.Tensor
 * @author Kacper Lubisz
 */
internal object Model {

    const val TENSOR_FLOW_NATIVES = "/tensorflow_jni.dll"
    const val MODEL_DIR: String = "/model/"
    val MODEL_FILES = listOf(
            MODEL_DIR + "saved_model.pbtxt",
            MODEL_DIR + "variables/variables.data-00000-of-00001",
            MODEL_DIR + "variables/variables.index"
    )

    private lateinit var tensorFlowSession: Session

    /**
     *  This just loads the TensorFlow model form disc
     *  @param modelDir the file which points to the folder that stores the model files
     */
    fun load(modelDir: File) {

        tensorFlowSession = SavedModelBundle.load(modelDir.absolutePath, "serve").session()

    }

    const val FFT_SIZE: Int = 4096 // the size of the fft used, equivalent to the input size
    const val START_PITCH: Int = 24 // the lowest pitch the model outputs

    const val END_PITCH: Int = 60
    const val PITCH_RANGE: Int = END_PITCH - START_PITCH
    val POSSIBLE_PITCHES = START_PITCH..END_PITCH
    const val CONFIDENCE_CUT_OFF = .5 // the confidence below which predictions will be discarded

    const val MEL_BINS_AMOUNT: Int = 124 // the size of the output spectrum

    // The names of tensors in the model
    private const val INPUT_TENSOR_NAME: String = "inputs"
    private const val OUTPUT_TENSOR_NAME: String = "predictions"
    private const val ENQUEUE_START_INPUTS: String = "enqueue_start_inputs"
    private const val ENQUEUE_NEW_INPUT: String = "enqueue_new_inputs"
    private const val DEQUEUE_INPUTS: String = "deque_inputs"
    private const val MEL_BINS_TENSOR: String = "mel_bins"
    private const val DEPHASED_SAMPLES: String = "de_phased_reconstruction"
    private const val DE_PHASED_POWER: String = "de_phased_rms"

    // The TensorFlow session which is an instance of the execution of the TensorFlow computation

    private val samplesInputBuffer: FloatBuffer // The FloatBuffer used for writing the samples and feeding them into the Model
    private val noteOutputBuffer: FloatBuffer // The buffer used for reading predictions from the Model
    private val spectrumOutputBuffer: FloatBuffer // The buffer used for reading the spectrum output from the Model
    private val dePhasedBuffer: FloatBuffer // The buffer used for reading the depahsed samples from the Model

    private var isSet = false

    init {

        // create the buffers
        samplesInputBuffer = FloatBuffer.allocate(FFT_SIZE)
        noteOutputBuffer = FloatBuffer.allocate(PITCH_RANGE)
        spectrumOutputBuffer = FloatBuffer.allocate(MEL_BINS_AMOUNT)
        dePhasedBuffer = FloatBuffer.allocate(FFT_SIZE)

    }

    /**
     * This method forwards the samples through the TensorFlow model of a new TimeStep
     *
     * @param samples The sound samples for this TimeStep
     * @return The object that represents the output of the model at this TimeStep
     *
     * @see TimeStep
     * @see StepOutput
     */
    internal fun feedForward(samples: FloatArray): StepOutput {

        samplesInputBuffer.rewind()
        samplesInputBuffer.put(samples)
        samplesInputBuffer.rewind()

        synchronized(tensorFlowSession) {
            // locks session to prevent concurrent modification

            val results = tensorFlowSession.runner()
                    .addTarget(ENQUEUE_NEW_INPUT)
                    .fetch(OUTPUT_TENSOR_NAME)
                    .fetch(MEL_BINS_TENSOR)
                    .fetch(DEPHASED_SAMPLES)
                    .fetch(DE_PHASED_POWER)
                    .feed(INPUT_TENSOR_NAME,
                            Tensor.create(longArrayOf(Model.FFT_SIZE.toLong()), samplesInputBuffer))
                    .run()
            // to understand fetches and feeds, see Model in train.py

            noteOutputBuffer.rewind()
            spectrumOutputBuffer.rewind()
            dePhasedBuffer.rewind()
            results[0].writeTo(noteOutputBuffer) // result[0] = OUTPUT_TENSOR_NAME
            results[1].writeTo(spectrumOutputBuffer) // result[1] = MEL_BINS_TENSOR
            results[2].writeTo(dePhasedBuffer) // result[1] = DEPHASED_SAMPLES
            return StepOutput(
                    noteOutputBuffer.array().copyOf(),
                    spectrumOutputBuffer.array().copyOf(),
                    dePhasedBuffer.array().copyOf(),
                    results[3].floatValue()
            )

        }


    }

    /**
     * This resets or initialises the sample queue inside the tensorflow model
     */
    internal fun setQueue(samples: FloatArray) {

        samplesInputBuffer.rewind()
        samplesInputBuffer.put(samples)
        samplesInputBuffer.rewind()

        synchronized(tensorFlowSession) {

            if (isSet)
                tensorFlowSession.runner().addTarget(DEQUEUE_INPUTS)
                        .run()


            tensorFlowSession.runner()
                    .addTarget(ENQUEUE_START_INPUTS)
                    .feed(INPUT_TENSOR_NAME,
                            Tensor.create(longArrayOf(1, 1, Model.FFT_SIZE.toLong()), samplesInputBuffer))
                    .run()

            isSet = true

        }
    }

    /**
     * This data class is the output of each TimeStep created by the model
     *
     * @see Model
     * @see TimeStep
     *
     * @property predictions The list of predictions for each pitch created by the model
     * @property spectrum The spectrum of the samples of a TimeStep
     * @property dePhased The dePhased visualisation of the TimeStep
     * @property dePhasedPower A representation of the volume of each step
     */
    internal data class StepOutput(val predictions: FloatArray, val spectrum: FloatArray, val dePhased: FloatArray, val dePhasedPower: Float) {

        /* auto generated equals and hashcode*/
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StepOutput

            if (!Arrays.equals(predictions, other.predictions)) return false
            if (!Arrays.equals(spectrum, other.spectrum)) return false
            if (!Arrays.equals(dePhased, other.dePhased)) return false
            if (dePhasedPower != other.dePhasedPower) return false

            return true
        }

        override fun hashCode(): Int {
            var result = Arrays.hashCode(predictions)
            result = 31 * result + Arrays.hashCode(spectrum)
            result = 31 * result + Arrays.hashCode(dePhased)
            result = 31 * result + dePhasedPower.hashCode()
            return result
        }

    }

}