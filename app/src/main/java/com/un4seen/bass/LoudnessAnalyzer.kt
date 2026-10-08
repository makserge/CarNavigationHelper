package com.un4seen.bass

import android.content.Context
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

// Integrated loudness (LUFS) and linear sample peak of a whole song
data class Loudness(val lufs: Double, val peak: Double)

// Volume normalisation ReplayGain 2 style: one static gain per song from its loudness after
// EBU R128 / ITU-R BS.1770-4, no compression. The file is decoded with its own BASS decode stream,
// so measure() runs on a background thread and never touches the playing stream
object LoudnessAnalyzer {
    private const val TAG = "Loudness"

    private const val TARGET_LUFS = -18.0
    // Assumed for a song that is not measured yet, typical for modern masters
    private const val DEFAULT_LUFS = -11.0
    private const val MIN_GAIN_DB = -24.0
    private const val MAX_GAIN_DB = 12.0

    private const val ABSOLUTE_GATE_LUFS = -70.0
    private const val RELATIVE_GATE_LU = -10.0
    private const val BUFFER_BYTES = 65536

    // The peak is unknown, so it never goes above 0 dB
    val DEFAULT_GAIN_DB = gainDb(DEFAULT_LUFS, null)

    // Target minus the song's loudness, limited so that the loudest sample does not clip
    fun gainDb(lufs: Double, peak: Double?): Double {
        val gain = (TARGET_LUFS - lufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val maxGain = if (peak != null && peak > 0.0) -20.0 * log10(peak) else 0.0
        return min(gain, maxGain)
    }

    // Null when the file can not be opened or decoded
    fun measure(context: Context, uri: Uri, fileName: String): Loudness? {
        val handle = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                val flags = BASS.BASS_STREAM_DECODE or BASS.BASS_SAMPLE_FLOAT
                when {
                    fileName.endsWith(".flac", ignoreCase = true) -> BASSFLAC.BASS_FLAC_StreamCreateFile(it, 0, 0, flags)
                    fileName.endsWith(".ape", ignoreCase = true) -> BASSAPE.BASS_APE_StreamCreateFile(it, 0, 0, flags)
                    else -> BASS.BASS_StreamCreateFile(it, 0, 0, flags)
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Open File Error: $e")
            0
        }
        if (handle == 0) {
            Log.e(TAG, "$fileName: not measured, BASS error ${BASS.BASS_ErrorGetCode()}")
            return null
        }

        return try {
            val loudness = measureStream(handle)
            if (loudness == null) {
                Log.e(TAG, "$fileName: not measured, BASS error ${BASS.BASS_ErrorGetCode()}")
            } else {
                Log.d(TAG, String.format(Locale.US, "%s: %.1f LUFS, peak %.3f, gain %.1f dB",
                    fileName, loudness.lufs, loudness.peak, gainDb(loudness.lufs, loudness.peak)))
            }
            loudness
        } catch (e: Exception) {
            Log.e(TAG, "$fileName: not measured: $e")
            null
        } finally {
            BASS.BASS_StreamFree(handle)
        }
    }

    private fun measureStream(handle: Int): Loudness? {
        val info = BASS.BASS_CHANNELINFO()
        if (!BASS.BASS_ChannelGetInfo(handle, info) || info.freq <= 0 || info.chans <= 0) return null
        val chans = info.chans
        val weights = DoubleArray(chans) { channelWeight(it, chans) }

        // K-weighting for the file's sample rate (sample rate independent design of libebur128):
        // a high shelf for the head, then the RLB high-pass with the numerator 1, -2, 1
        var k = tan(PI * 1681.974450955533 / info.freq)
        var q = 0.7071752369554196
        val vh = 10.0.pow(3.999843853973347 / 20.0)
        val vb = vh.pow(0.4996667741545416)
        var a0 = 1.0 + k / q + k * k
        val sb0 = (vh + vb * k / q + k * k) / a0
        val sb1 = 2.0 * (k * k - vh) / a0
        val sb2 = (vh - vb * k / q + k * k) / a0
        val sa1 = 2.0 * (k * k - 1.0) / a0
        val sa2 = (1.0 - k / q + k * k) / a0
        k = tan(PI * 38.13547087602444 / info.freq)
        q = 0.5003270373238773
        a0 = 1.0 + k / q + k * k
        val ha1 = 2.0 * (k * k - 1.0) / a0
        val ha2 = (1.0 - k / q + k * k) / a0

        // Two biquad states per channel (transposed direct form II)
        val state = DoubleArray(chans * 4)
        // Weighted mean square of every 100 ms step, a 400 ms block with 75% overlap is 4 steps
        val stepFrames = max(1, info.freq / 10)
        val steps = ArrayList<Double>()
        var stepSum = 0.0
        var stepFrame = 0
        var ch = 0
        var peak = 0.0

        val buffer = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder())
        val floats = buffer.asFloatBuffer()
        val samples = FloatArray(BUFFER_BYTES / 4)
        while (true) {
            val bytes = BASS.BASS_ChannelGetData(handle, buffer, BUFFER_BYTES or BASS.BASS_DATA_FLOAT)
            // -1 with BASS_ERROR_ENDED is the end of the file, any other error a broken file
            if (bytes < 0 && BASS.BASS_ErrorGetCode() != BASS.BASS_ERROR_ENDED) return null
            if (bytes <= 0) break
            val count = bytes / 4
            floats.position(0)
            floats.get(samples, 0, count)
            for (i in 0 until count) {
                val x = samples[i].toDouble()
                peak = max(peak, abs(x))
                val s = ch * 4
                val y1 = sb0 * x + state[s]
                state[s] = sb1 * x - sa1 * y1 + state[s + 1]
                state[s + 1] = sb2 * x - sa2 * y1
                val y2 = y1 + state[s + 2]
                state[s + 2] = -2.0 * y1 - ha1 * y2 + state[s + 3]
                state[s + 3] = y1 - ha2 * y2
                stepSum += weights[ch] * y2 * y2
                if (++ch == chans) {
                    ch = 0
                    if (++stepFrame == stepFrames) {
                        steps.add(stepSum / stepFrames)
                        stepSum = 0.0
                        stepFrame = 0
                    }
                }
            }
        }

        // Block power = sum over the channels of weight * mean square, its loudness -0.691 + 10 * log10(power)
        val blocks = DoubleArray(max(0, steps.size - 3)) { (steps[it] + steps[it + 1] + steps[it + 2] + steps[it + 3]) / 4.0 }
        val absoluteGate = power(ABSOLUTE_GATE_LUFS)
        val aboveAbsolute = blocks.filter { it > absoluteGate }
        // Silence or shorter than one block
        if (aboveAbsolute.isEmpty()) return Loudness(ABSOLUTE_GATE_LUFS, peak)
        val relativeGate = power(loudness(aboveAbsolute.average()) + RELATIVE_GATE_LU)
        val gated = aboveAbsolute.filter { it > relativeGate }
        return Loudness(loudness(gated.average()), peak)
    }

    private fun loudness(power: Double) = -0.691 + 10.0 * log10(power)

    private fun power(lufs: Double) = 10.0.pow((lufs + 0.691) / 10.0)

    // BS.1770 channel weights in the WAVE order that BASS decodes to: L, R and C at 1.0, the LFE of 5.1 / 7.1
    // is not counted, surround channels at 1.41. Mono plays on both speakers, so it counts as dual mono
    private fun channelWeight(channel: Int, chans: Int): Double = when {
        chans == 1 -> 2.0
        channel < 2 -> 1.0
        chans == 4 -> 1.41
        channel == 2 -> 1.0
        channel == 3 && chans >= 6 -> 0.0
        else -> 1.41
    }
}
