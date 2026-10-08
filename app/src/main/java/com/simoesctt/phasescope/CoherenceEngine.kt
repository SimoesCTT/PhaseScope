package com.simoesctt.phasescope

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

object CoherenceEngine {

    data class Result(
        val rTimeline: DoubleArray,     // Kuramoto order parameter over time
        val matrix: Array<DoubleArray>, // N x N phase coherence
        val meanR: Double
    )

    /**
     * Compute phase coherence matrix and Kuramoto order parameter timeline.
     *
     * @param channels     N channels, each an array of samples
     * @param sampleRate   samples per second
     * @param bandLow      bandpass low cutoff (Hz)
     * @param bandHigh     bandpass high cutoff (Hz)
     * @param windowSec    sliding window length for r(t)
     * @param hopSec       hop between windows
     */
    fun analyze(
        channels: Array<DoubleArray>,
        sampleRate: Double,
        bandLow: Double,
        bandHigh: Double,
        windowSec: Double = 1.0,
        hopSec: Double = 0.25
    ): Result {
        val n = channels.size
        require(n >= 2) { "Need at least 2 channels" }

        val length = channels[0].size
        require(channels.all { it.size == length }) { "Channels must be equal length" }

        // 1. Bandpass filter each channel
        val filtered = Array(n) { filter(channels[it], sampleRate, bandLow, bandHigh) }

        // 2. Analytic signal → instantaneous phase for each channel
        val phases = Array(n) { hilbertPhase(filtered[it]) }

        // 3. Phase coherence matrix (mean of exp(i(phi_i - phi_j)))
        val matrix = Array(n) { i ->
            DoubleArray(n) { j ->
                var re = 0.0; var im = 0.0
                for (t in 0 until length) {
                    val d = phases[i][t] - phases[j][t]
                    re += cos(d); im += sin(d)
                }
                sqrt(re * re + im * im) / length
            }
        }

        // 4. Kuramoto order parameter over sliding windows
        val winSize = (windowSec * sampleRate).toInt().coerceAtLeast(4)
        val hopSize = (hopSec * sampleRate).toInt().coerceAtLeast(1)
        val nFrames = ((length - winSize) / hopSize) + 1
        val timeline = DoubleArray(nFrames.coerceAtLeast(1))

        for (f in 0 until nFrames) {
            val s = f * hopSize
            val e = s + winSize
            var sumR = 0.0
            for (t in s until e) {
                var reT = 0.0
                var imT = 0.0
                for (i in 0 until n) {
                    reT += cos(phases[i][t])
                    imT += sin(phases[i][t])
                }
                sumR += sqrt(reT * reT + imT * imT) / n
            }
            timeline[f] = if (e > s) sumR / (e - s) else 0.0
        }

        var sumR = 0.0
        for (t in 0 until length) {
            var reT = 0.0
            var imT = 0.0
            for (i in 0 until n) {
                reT += cos(phases[i][t])
                imT += sin(phases[i][t])
            }
            sumR += sqrt(reT * reT + imT * imT) / n
        }
        val meanR = if (length > 0) sumR / length else 0.0

        return Result(timeline, matrix, meanR)
    }

    /** 4th-order Butterworth bandpass, forward-backward (zero phase). */
    private fun filter(x: DoubleArray, fs: Double, lo: Double, hi: Double): DoubleArray {
        val stages = butter4Bandpass(lo, hi, fs)
        var y = x
        for (s in stages) y = biquad(y, s)
        // Reverse, filter again with same stages, reverse back → zero-phase
        val rev = y.reversedArray()
        var yr = rev
        for (s in stages) yr = biquad(yr, s)
        return yr.reversedArray()
    }

    /** Compute 4th-order Butterworth bandpass as two cascaded biquad stages. */
    private fun butter4Bandpass(lo: Double, hi: Double, fs: Double): Array<Biquad> {
        val n = 4  // order
        // Prewarp the digital band edges
        val wLo = 2.0 * fs * kotlin.math.tan(PI * lo / fs)
        val wHi = 2.0 * fs * kotlin.math.tan(PI * hi / fs)
        val bw = wHi - wLo
        val w0 = kotlin.math.sqrt(wLo * wHi)
        val bwNorm = bw / w0
        // For an analog Butterworth bandpass, poles come in conjugate pairs
        // We build the digital filter via the RBJ biquad cascade with Q from Butterworth poles.
        // Butterworth 4th-order pole angles: (2k+1)*pi/(2n) for k=0..n/2-1
        // For bandpass, Q = w0 / bw * (1 / (2*cos(theta)))
        val stages = mutableListOf<Biquad>()
        for (k in 0 until n / 2) {
            val theta = (2.0 * k + 1.0) * PI / (2.0 * n)
            val qButter = 1.0 / (2.0 * kotlin.math.cos(theta))
            // Bandpass Q: Q_bp = (w0 / bw) * qButter  (for the lowpass-to-bandpass transform)
            val q = (w0 / bw) * qButter
            stages.add(rbjBandpass(lo, hi, fs, q))
        }
        return stages.toTypedArray()
    }

    /** RBJ bandpass biquad at center frequency (lo+hi)/2 with bandwidth (hi-lo) and given Q. */
    private fun rbjBandpass(lo: Double, hi: Double, fs: Double, q: Double): Biquad {
        val f0 = kotlin.math.sqrt(lo * hi)         // geometric center
        val w0 = 2.0 * PI * f0 / fs
        val alpha = kotlin.math.sin(w0) / (2.0 * q)
        val cosw0 = kotlin.math.cos(w0)
        val a0 = 1.0 + alpha
        // Constant 0 dB peak gain bandpass
        return Biquad(
            b0 = alpha / a0,
            b1 = 0.0,
            b2 = -alpha / a0,
            a1 = -2.0 * cosw0 / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private data class Biquad(val b0: Double, val b1: Double, val b2: Double,
                              val a1: Double, val a2: Double)

    private fun biquad(x: DoubleArray, c: Biquad): DoubleArray {
        val y = DoubleArray(x.size)
        var x1: Double = 0.0
        var x2: Double = 0.0
        var y1: Double = 0.0
        var y2: Double = 0.0
        for (i in x.indices) {
            val out: Double = c.b0 * x[i] + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
            y[i] = out
            x2 = x1; x1 = x[i]
            y2 = y1; y1 = out
        }
        return y
    }

    /** Hilbert transform phase via FFT (proper analytic signal). */
    private fun hilbertPhase(x: DoubleArray): DoubleArray {
        val n = x.size
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        for (i in 0 until n) data[2 * i] = x[i]
        fft.complexForward(data)

        // Analytic signal construction:
        // - Keep DC (bin 0) and Nyquist (bin n/2) unchanged
        // - Double the positive frequencies (bins 1 .. n/2 - 1)
        // - Zero the negative frequencies (bins n/2 + 1 .. n - 1)
        for (k in 1 until n / 2) {
            data[2 * k] *= 2.0
            data[2 * k + 1] *= 2.0
        }
        for (k in n / 2 + 1 until n) {
            data[2 * k] = 0.0
            data[2 * k + 1] = 0.0
        }

        fft.complexInverse(data, false)

        val phase = DoubleArray(n)
        for (i in 0 until n) {
            phase[i] = atan2(data[2 * i + 1], data[2 * i])
        }
        return phase
    }
}
