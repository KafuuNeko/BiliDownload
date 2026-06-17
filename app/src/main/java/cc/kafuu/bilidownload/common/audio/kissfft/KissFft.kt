package cc.kafuu.bilidownload.common.audio.kissfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small Kotlin FFT plan shaped after KissFFT's simple, out-of-place API.
 *
 * The music analyzer only uses power-of-two windows, so this implementation keeps
 * the plan compact and radix-2 based while preserving floating point, unscaled
 * forward transforms.
 */
class KissFft(
    private val nfft: Int,
    private val inverse: Boolean = false
) {
    private val bitReverse = IntArray(nfft)
    private val twiddleReal = FloatArray(nfft / 2)
    private val twiddleImag = FloatArray(nfft / 2)

    init {
        require(nfft > 1 && nfft and (nfft - 1) == 0) {
            "KissFft requires a power-of-two nfft."
        }

        val bits = Integer.numberOfTrailingZeros(nfft)
        for (i in 0 until nfft) {
            bitReverse[i] = Integer.reverse(i) ushr (Int.SIZE_BITS - bits)
        }

        val sign = if (inverse) 1.0 else -1.0
        for (i in 0 until nfft / 2) {
            val phase = sign * 2.0 * PI * i / nfft
            twiddleReal[i] = cos(phase).toFloat()
            twiddleImag[i] = sin(phase).toFloat()
        }
    }

    fun transform(input: FloatArray, output: FloatArray = FloatArray(nfft * 2)): FloatArray {
        require(input.size >= nfft * 2) { "Input must contain $nfft complex samples." }
        require(output.size >= nfft * 2) { "Output must contain $nfft complex samples." }

        for (i in 0 until nfft) {
            val src = bitReverse[i] * 2
            val dst = i * 2
            output[dst] = input[src]
            output[dst + 1] = input[src + 1]
        }

        transformPrepared(output)
        return output
    }

    fun transformReal(
        input: FloatArray,
        window: FloatArray,
        output: FloatArray = FloatArray(nfft * 2)
    ): FloatArray {
        require(input.size >= nfft) { "Input must contain $nfft real samples." }
        require(window.size >= nfft) { "Window must contain $nfft samples." }
        require(output.size >= nfft * 2) { "Output must contain $nfft complex samples." }

        for (i in 0 until nfft) {
            val src = bitReverse[i]
            val dst = i * 2
            output[dst] = input[src] * window[src]
            output[dst + 1] = 0f
        }

        transformPrepared(output)
        return output
    }

    fun transformRealRing(
        ring: FloatArray,
        startIndex: Int,
        window: FloatArray,
        output: FloatArray = FloatArray(nfft * 2)
    ): FloatArray {
        require(ring.size >= nfft) { "Ring must contain at least $nfft real samples." }
        require(startIndex in 0 until nfft) { "Start index must be inside the FFT window." }
        require(window.size >= nfft) { "Window must contain $nfft samples." }
        require(output.size >= nfft * 2) { "Output must contain $nfft complex samples." }

        for (i in 0 until nfft) {
            val logicalIndex = bitReverse[i]
            var src = startIndex + logicalIndex
            if (src >= nfft) src -= nfft
            val dst = i * 2
            output[dst] = ring[src] * window[logicalIndex]
            output[dst + 1] = 0f
        }

        transformPrepared(output)
        return output
    }

    private fun transformPrepared(output: FloatArray) {
        var halfSize = 1
        while (halfSize < nfft) {
            val tableStep = nfft / (halfSize * 2)
            var i = 0
            while (i < nfft) {
                var twiddleIndex = 0
                for (j in 0 until halfSize) {
                    val evenIndex = (i + j) * 2
                    val oddIndex = evenIndex + halfSize * 2

                    val oddReal = output[oddIndex]
                    val oddImag = output[oddIndex + 1]
                    val wr = twiddleReal[twiddleIndex]
                    val wi = twiddleImag[twiddleIndex]
                    val tempReal = oddReal * wr - oddImag * wi
                    val tempImag = oddReal * wi + oddImag * wr

                    val evenReal = output[evenIndex]
                    val evenImag = output[evenIndex + 1]
                    output[oddIndex] = evenReal - tempReal
                    output[oddIndex + 1] = evenImag - tempImag
                    output[evenIndex] = evenReal + tempReal
                    output[evenIndex + 1] = evenImag + tempImag

                    twiddleIndex += tableStep
                }
                i += halfSize * 2
            }
            halfSize *= 2
        }

        if (inverse) {
            val scale = 1f / nfft
            for (i in 0 until nfft * 2) output[i] *= scale
        }
    }
}
