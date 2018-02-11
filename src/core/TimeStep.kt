package core

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min


/**
 * This class is for storing the data related to each section of time.
 * This object interacts with the model by passing the samples to it.
 *
 * @author Kacper Lubisz
 *
 * @see Model
 * @see StepOutput
 * @see Note
 *
 * @property section The section that this time step belongs to
 * @property dePhased The reconstructed samples after the phase of the sinusoid that make it up is removed
 * @property melImage The image that can be drawn on screen of the frequencies on the mel scale
 * @property modelOutput The object that represents the outputs of the Model
 * @property notes The notes that are present in the time step
 */
class TimeStep private constructor(val section: Section, private val sampleStart: Int, private val time: Int, private val previous: TimeStep? = null) { // start in steps

    constructor(section: Section, sampleStart: Int, previous: TimeStep?) :
            this(section, sampleStart, (previous?.time ?: -1) + 1, previous)

    var melImage: Image
    var noteImage: Image

    val dePhased: FloatArray
    val dePhasedPower: Float

    private val pitches: List<Int>

    private val samples: FloatArray
        get() {
            synchronized(section.recording) {
                return section.samples.subList(sampleStart, sampleStart + SoundProcessingController.FRAME_SIZE).toFloatArray()
            }
        }

    val notes: List<Note>

    init {

        val samples = samples
        // so that the samples don't need to be sub-listed twice

        if (time == 0) {
            Model.setQueue(samples)
        }

        val (predictions, spectrum, dePhased, dePhasedPower) = Model.feedForward(samples)
        this.dePhased = dePhased
        this.dePhasedPower = dePhasedPower

        pitches = predictions.mapIndexed { index, confidence -> index to confidence }
                .filter {
                    return@filter it.second >= Model.CONFIDENCE_CUT_OFF
                }.map {
                    it.first + Model.START_PITCH
                }

        // identify notes present in the current timestep and link them with the ones in the previous one to make one note object
        notes = if (previous == null) {
            pitches.map {
                Note(it, time, 1)
            }
        } else {
            pitches.map {
                if (previous.pitches.contains(it)) {
                    val note = previous.notes.find { p -> p.pitch == it }!!
                    note.duration++
                    return@map note
                } else {
                    Note(it, time, 1)
                }
            }
        }

        // This buffered image is a slice of the spectrogram at this time step
        val melImage = BufferedImage(1, Model.MEL_BINS_AMOUNT, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until Model.MEL_BINS_AMOUNT) {
            val value = ((min(max(spectrum[y], minMagnitude), maxMagnitude) - minMagnitude) / (maxMagnitude - minMagnitude))
            melImage.setRGB(0, y, mapToColour(value))
        }
        val noteImage = BufferedImage(1, Model.PITCH_RANGE, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until Model.PITCH_RANGE) {
            val value = min(max(predictions[y], 0f), 1f)
            noteImage.setRGB(0, y, mapToColour(value))
        }

        this.melImage = SwingFXUtils.toFXImage(melImage, null)
        this.noteImage = SwingFXUtils.toFXImage(noteImage, null)

    }

    companion object {

        /**
         * This is the range between which the volumes should be interpolated
         */
        private const val maxMagnitude = -1.0f
        private const val minMagnitude = -16.0f

        /**
         * The colours can interpolate between to create a scale.
         * I chose these colors because I wanted to reduce the amount of colours that the heat map uses so that
         * I can use other colours over it
         */
        private val viridisColourMap: Array<Color> = arrayOf(
                Color(70, 6, 90),
                Color(54, 91, 141),
                Color(47, 180, 124),
                Color(248, 230, 33)
        )


        /**
         * Makes a value between 0 and 1 to a colour for the heat map
         * @param x The value to be mapped
         * @param colours The list of colours that are to be interpolated between, by default colourMapColours
         */
        private fun mapToColour(x: Float, colours: Array<Color> = viridisColourMap): Int {
            val h = (x * (colours.size - 1)).toInt()
            val f = (x * (colours.size - 1)) % 1

            return if (h == colours.size - 1)
                rgbToInt(colours[h])
            else
                interpolateColourToInt(colours[h], colours[h + 1], f)
        }

        /**
         * Interpolates between two colours and then converts the resulting colour to the 32 bit integer that
         * represents that colour
         * @param a The start colour
         * @param b The end colour
         * @param x the point in the interpolation
         */
        private fun interpolateColourToInt(a: Color, b: Color, x: Float): Int {

            return rgbToInt(a.red * (1 - x) + b.red * x,
                    a.green * (1 - x) + b.green * x,
                    a.blue * (1 - x) + b.blue * x)

        }

        /**
         * A wrapper function which automatically casts floats to integers
         * @param r The amount of red, in 0f..255f
         * @param g The amount of green, in 0f..255f
         * @param b The amount of blue, in 0f..255f
         * @return the 32 bit representation of the colour
         */
        private fun rgbToInt(r: Float, g: Float, b: Float): Int = rgbToInt(r.toInt(), g.toInt(), b.toInt())

        /**
         * Convert a colour in rbg [0-255] format to the integer that represents that colour
         * @param r The amount of red, in 0..255
         * @param g The amount of green, in 0..255
         * @param b The amount of blue, in 0..255
         * @return the 32 bit representation of the colour
         */
        private fun rgbToInt(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b

        /**
         * Converts the colour object into the 32 bit integer that represents it
         * @col The colour to be converted
         * @return the 32 bit representation of the colour
         */
        private fun rgbToInt(col: Color): Int = rgbToInt(col.red, col.green, col.blue)

    }

}