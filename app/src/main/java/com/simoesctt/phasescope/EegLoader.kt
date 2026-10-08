package com.simoesctt.phasescope

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

object EegLoader {

    data class Eeg(
        val channels: Array<DoubleArray>,
        val sampleRate: Double,
        val channelNames: List<String>
    )

    /**
     * Load a CSV EEG file. Expected format:
     *   - First row: header with channel names
     *   - First column: time (seconds) OR sample index
     *   - Remaining columns: channel samples
     *
     * Falls back to assuming all columns are channels if header detection fails.
     * Sample rate is inferred from the time column if present, else defaults to 256 Hz.
     */
    fun loadCsv(ctx: Context, uri: Uri): Eeg {
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open file" }
            val reader = BufferedReader(InputStreamReader(input))

            val headerLine = reader.readLine() ?: error("Empty file")
            val header = headerLine.split(",", ";", "\t").map { it.trim() }

            val rows = mutableListOf<List<Double>>()
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    val parts = line.split(",", ";", "\t")
                    if (parts.size >= 2) {
                        val nums = parts.mapNotNull { it.trim().toDoubleOrNull() }
                        if (nums.size >= 2) rows.add(nums)
                    }
                }
                line = reader.readLine()
            }

            require(rows.isNotEmpty()) { "No data rows found" }

            val cols = rows.minOf { it.size }
            val firstIsTime = header.firstOrNull()?.lowercase()?.let {
                it.contains("time") || it.contains("sec") || it.contains("t ")
            } ?: false

            // Extract per-channel arrays
            val channelNames: List<String>
            val channelStart: Int
            if (firstIsTime) {
                channelNames = header.drop(1).take(cols - 1)
                channelStart = 1
            } else {
                channelNames = if (header.size == cols) header
                else List(cols) { "Ch${it + 1}" }
                channelStart = 0
            }

            val nCh = cols - channelStart
            val nSamples = rows.size
            val channels = Array(nCh) { DoubleArray(nSamples) }
            for (r in 0 until nSamples) {
                val row = rows[r]
                for (c in 0 until nCh) {
                    channels[c][r] = row[channelStart + c]
                }
            }

            // Estimate sample rate
            val sampleRate = if (firstIsTime && rows.size > 1) {
                val t0 = rows[0][0]
                val t1 = rows[rows.size - 1][0]
                val dt = (t1 - t0) / (rows.size - 1)
                if (dt > 0) 1.0 / dt else 256.0
            } else 256.0

            return Eeg(channels, sampleRate, channelNames)
        }
    }
}
