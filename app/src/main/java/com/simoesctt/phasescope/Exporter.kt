package com.simoesctt.phasescope

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    /** Save coherence matrix + r timeline as a CSV in cacheDir and return the file. */
    fun exportCsv(
        ctx: Context,
        matrix: Array<DoubleArray>,
        rTimeline: DoubleArray,
        bandName: String,
        channelNames: List<String>
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(ctx.cacheDir, "phasescope_$stamp.csv")

        FileWriter(file).use { w ->
            w.write("# PhaseScope export\n")
            w.write("# band: $bandName\n")
            w.write("# date: $stamp\n")
            w.write("# channels: ${channelNames.joinToString(",")}\n")
            w.write("\n")

            w.write("# Kuramoto r(t) timeline\n")
            w.write("frame,r\n")
            rTimeline.forEachIndexed { i, v ->
                w.write("$i,${"%.6f".format(v)}\n")
            }
            w.write("\n")

            w.write("# Coherence matrix\n")
            w.write("ch," + channelNames.joinToString(",") + "\n")
            matrix.forEachIndexed { i, row ->
                w.write(channelNames.getOrElse(i) { "ch$i" } + ",")
                w.write(row.joinToString(",") { "%.6f".format(it) })
                w.write("\n")
            }
        }
        return file
    }

    /** Share any file via Android's share sheet. */
    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share"))
    }
}
