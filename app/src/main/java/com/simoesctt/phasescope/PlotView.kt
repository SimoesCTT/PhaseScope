package com.simoesctt.phasescope

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a coherence matrix (N x N heatmap) and a timeline of the
 * Kuramoto order parameter r(t) below it.
 */
class PlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var matrix: Array<DoubleArray>? = null
        set(v) { field = v; invalidate() }

    var rTimeline: DoubleArray? = null
        set(v) { field = v; invalidate() }

    var bandName: String = ""
        set(v) { field = v; invalidate() }

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#283593")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7986CB")
        textSize = 22f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 20f

        val m = matrix
        val timeline = rTimeline

        // If nothing to draw yet, show placeholder
        if (m == null || timeline == null) {
            canvas.drawText("No data", padding, h / 2, textPaint)
            return
        }

        val n = m.size
        if (n == 0) return

        // Layout: coherence matrix takes top ~65%, timeline bottom ~30%
        val matrixHeight = h * 0.65f
        val matrixSize = minOf(w - 2 * padding, matrixHeight - padding)
        val cellSize = matrixSize / n

        val mx0 = padding
        val my0 = padding

        // Draw coherence matrix
        for (i in 0 until n) {
            for (j in 0 until n) {
                val c = m[i][j].coerceIn(0.0, 1.0)
                // Plasma-like gradient: dark blue -> cyan -> yellow
                val color = coherenceColor(c)
                cellPaint.color = color
                cellPaint.style = Paint.Style.FILL
                val x = mx0 + j * cellSize
                val y = my0 + i * cellSize
                canvas.drawRect(x, y, x + cellSize, y + cellSize, cellPaint)
            }
        }

        // Grid lines
        for (k in 0..n) {
            val p = mx0 + k * cellSize
            canvas.drawLine(p, my0, p, my0 + matrixSize, gridPaint)
            canvas.drawLine(mx0, my0 + k * cellSize, mx0 + matrixSize, my0 + k * cellSize, gridPaint)
        }

        // Band label
        canvas.drawText(bandName, mx0, my0 + matrixSize + 24f, textPaint)

        // Timeline
        if (timeline.size > 1) {
            val timelineTop = h * 0.72f
            val timelineBottom = h - padding
            val timelineHeight = timelineBottom - timelineTop

            // Background line at r=1
            gridPaint.color = Color.parseColor("#283593")
            canvas.drawLine(padding, timelineTop, w - padding, timelineTop, gridPaint)

            // Baseline at r=0
            canvas.drawLine(padding, timelineBottom, w - padding, timelineBottom, gridPaint)

            // The r(t) curve
            val dx = (w - 2 * padding) / (timeline.size - 1).toFloat()
            val path = android.graphics.Path()
            for (i in timeline.indices) {
                val x = padding + i * dx
                val y = timelineBottom - (timeline[i].coerceIn(0.0, 1.0) * timelineHeight).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)

            // Axis labels
            canvas.drawText("r(t) over time", padding, timelineTop - 6f, textPaint)
        }
    }

    private fun coherenceColor(v: Double): Int {
        // Simple 5-stop heatmap
        // 0.0 = deep blue, 0.25 = blue, 0.5 = cyan, 0.75 = green, 1.0 = yellow
        val r: Int
        val g: Int
        val b: Int
        when {
            v < 0.25 -> {
                val t = v / 0.25
                r = 0
                g = (t * 100).toInt()
                b = (150 + t * 105).toInt()
            }
            v < 0.5 -> {
                val t = (v - 0.25) / 0.25
                r = 0
                g = (100 + t * 155).toInt()
                b = 255
            }
            v < 0.75 -> {
                val t = (v - 0.5) / 0.25
                r = (t * 150).toInt()
                g = 255
                b = (255 - t * 155).toInt()
            }
            else -> {
                val t = (v - 0.75) / 0.25
                r = (150 + t * 105).toInt()
                g = 255
                b = (100 - t * 100).toInt()
            }
        }
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
