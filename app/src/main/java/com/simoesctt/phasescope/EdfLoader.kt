package com.simoesctt.phasescope

import android.content.Context
import android.net.Uri
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Minimal EDF (European Data Format) loader.
 * Spec: https://www.edfplus.info/specs/edf.html
 *
 * Reads signals as Double arrays. Only handles EDF (not EDF+), and only
 * the "signals" portion — it does not currently handle annotations.
 */
object EdfLoader {

    data class Edf(
        val channels: Array<DoubleArray>,
        val channelNames: List<String>,
        val sampleRate: Double,           // of first signal (assumed uniform)
        val startDate: String,
        val startTime: String
    )

    fun load(ctx: Context, uri: Uri): Edf {
        ctx.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Cannot open EDF file" }
            return parse(raw)
        }
    }

    private fun parse(input: InputStream): Edf {
        // Header is 256 bytes fixed, then ns bytes per field
        val header = ByteArray(256)
        readFully(input, header)
        val h = String(header, Charsets.US_ASCII)

        val nSignals = h.substring(252, 256).trim().toInt()

        // Fixed-size header fields
        val nDataRecordsStr = h.substring(236, 244).trim()
        val nDataRecords = if (nDataRecordsStr.isEmpty()) 0 else nDataRecordsStr.toInt()
        val nDataRecordsDuration = h.substring(244, 252).trim().toDouble()

        // Variable-length fields follow, each of length ns * nSignals
        val labelBytes       = readField(input, nSignals, 16)
        readField(input, nSignals, 80)   // transducer type
        readField(input, nSignals, 8)    // physical dimension
        val physMinBytes     = readField(input, nSignals, 8)
        val physMaxBytes     = readField(input, nSignals, 8)
        val digMinBytes      = readField(input, nSignals, 8)
        val digMaxBytes      = readField(input, nSignals, 8)
        readField(input, nSignals, 80)   // prefiltering
        val samplesPerRecBytes = readField(input, nSignals, 8)
        readField(input, nSignals, 32)   // reserved

        val labels          = labelBytes.map { it.trim() }
        val physMin         = physMinBytes.map { it.trim().toDoubleOrNull() ?: 0.0 }
        val physMax         = physMaxBytes.map { it.trim().toDoubleOrNull() ?: 0.0 }
        val digMin          = digMinBytes.map { it.trim().toIntOrNull() ?: -32768 }
        val digMax          = digMaxBytes.map { it.trim().toIntOrNull() ?: 32767 }
        val samplesPerRec   = samplesPerRecBytes.map { it.trim().toIntOrNull() ?: 0 }

        // How many samples in each signal, total
        val nSignalsInt = nSignals
        val totalSamples = IntArray(nSignalsInt) { samplesPerRec[it] * nDataRecords }
        val channels = Array(nSignalsInt) { DoubleArray(totalSamples[it]) }
        val channelWriters = IntArray(nSignalsInt) { 0 }

        // Read data records
        for (rec in 0 until nDataRecords) {
            for (s in 0 until nSignalsInt) {
                val nSamp = samplesPerRec[s]
                val buf = ByteArray(nSamp * 2)
                readFully(input, buf)
                val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                // Precompute scaling
                val physRange = physMax[s] - physMin[s]
                val digRange = (digMax[s] - digMin[s]).toDouble()
                val scale = if (digRange != 0.0) physRange / digRange else 1.0
                for (i in 0 until nSamp) {
                    val raw = bb.getShort().toInt()   // signed 16-bit
                    val phys = physMin[s] + (raw - digMin[s]) * scale
                    channels[s][channelWriters[s]++] = phys
                }
            }
        }

        val fs = if (nDataRecordsDuration > 0 && samplesPerRec.isNotEmpty())
            samplesPerRec[0] / nDataRecordsDuration
        else 256.0

        // EDF stores start date as dd.mm.yy and time as hh.mm.ss
        val startDate = h.substring(168, 176).trim()
        val startTime = h.substring(176, 184).trim()

        return Edf(channels, labels, fs, startDate, startTime)
    }

    private fun readField(input: InputStream, n: Int, bytesPer: Int): List<String> {
        val buf = ByteArray(n * bytesPer)
        readFully(input, buf)
        val out = ArrayList<String>(n)
        for (i in 0 until n) {
            val start = i * bytesPer
            out.add(String(buf, start, bytesPer, Charsets.US_ASCII))
        }
        return out
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n <= 0) throw java.io.IOException("Unexpected end of file")
            read += n
        }
    }
}
