package com.example.mygidc.dashboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.mygidc.dashboard.HeadDashboardActivity.ViewMode

/**
 * Dot-based trend chart view.
 * X axis = date labels, Y axis = counts.
 * Supports three view modes:
 *   ViewMode.COMPLAINTS — blue dots + line
 *   ViewMode.RESOLVED   — pink dots + line
 *   ViewMode.BOTH       — both series
 *
 * Lines are drawn as STRAIGHT segments (lineTo only).
 * No cubicTo / quadTo → no curves.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DataPoint(val label: String, val complaints: Int, val resolved: Int)

    private var dataPoints: List<DataPoint> = emptyList()
    private var viewMode: ViewMode = ViewMode.BOTH

    // ── Paints ────────────────────────────────────────────────────

    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintComplaintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#17A2F3")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintResolvedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4F87")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintDotBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DCE3F3")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(7f, 7f), 0f)
    }
    private val paintAxis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0D8EF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8898AA")
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val paintYLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8898AA")
        textSize = 24f
        textAlign = Paint.Align.LEFT
    }
    private val paintValueBubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintValueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val paintEmptyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8898AA")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // ── Public API ────────────────────────────────────────────────

    fun setData(points: List<DataPoint>) {
        dataPoints = points
        invalidate()
    }

    fun setViewMode(mode: ViewMode) {
        viewMode = mode
        invalidate()
    }

    // ── Draw ──────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w    = width.toFloat()
        val h    = height.toFloat()
        val padL = 24f
        val padR = 52f
        val padT = 30f
        val padB = 52f

        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // ── Empty state ───────────────────────────────────────────
        if (dataPoints.isEmpty()) {
            canvas.drawText("No data available", w / 2f, h / 2f, paintEmptyText)
            return
        }

        // ── Determine max value based on view mode ─────────────────
        val maxVal = when (viewMode) {
            ViewMode.COMPLAINTS -> dataPoints.maxOf { it.complaints }.coerceAtLeast(1)
            ViewMode.RESOLVED   -> dataPoints.maxOf { it.resolved }.coerceAtLeast(1)
            ViewMode.BOTH       -> dataPoints.maxOf { maxOf(it.complaints, it.resolved) }.coerceAtLeast(1)
        }

        val n = dataPoints.size

        // ── Grid lines ────────────────────────────────────────────
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = padT + chartH * (1f - i.toFloat() / gridSteps)
            canvas.drawLine(padL, y, w - padR, y, paintGrid)
            val value = (maxVal * i / gridSteps)
            canvas.drawText(value.toString(), w - padR + 8f, y + 8f, paintYLabel)
        }

        // ── Axis line ─────────────────────────────────────────────
        canvas.drawLine(padL, padT + chartH, w - padR, padT + chartH, paintAxis)

        // ── Coordinate helpers ────────────────────────────────────
        fun xAt(i: Int): Float = if (n == 1)
            padL + chartW / 2f
        else
            padL + (i.toFloat() / (n - 1).toFloat()) * chartW

        fun yAt(v: Int): Float = padT + chartH * (1f - v.toFloat() / maxVal)

        val baseline = padT + chartH

        // ── Build and draw STRAIGHT connecting lines ───────────────
        //
        //  ✅ CHANGED: was cubicTo(cx, pY, cx, y, x, y) — smooth Bézier curve
        //  ✅ NOW:     lineTo(x, y)                       — straight line
        //
        if (n > 1) {
            val complaintPath = Path()
            val resolvedPath  = Path()

            dataPoints.forEachIndexed { i, dp ->
                val x  = xAt(i)
                val cy = yAt(dp.complaints)
                val ry = yAt(dp.resolved)

                if (i == 0) {
                    complaintPath.moveTo(x, cy)
                    resolvedPath.moveTo(x, ry)
                } else {
                    complaintPath.lineTo(x, cy)   // straight segment
                    resolvedPath.lineTo(x, ry)    // straight segment
                }
            }

            if (viewMode == ViewMode.COMPLAINTS || viewMode == ViewMode.BOTH) {
                canvas.drawPath(complaintPath, paintComplaintLine)
            }
            if (viewMode == ViewMode.RESOLVED || viewMode == ViewMode.BOTH) {
                canvas.drawPath(resolvedPath, paintResolvedLine)
            }
        }

        // ── Draw dots + value bubbles ─────────────────────────────
        dataPoints.forEachIndexed { i, dp ->
            val x  = xAt(i)
            val cy = yAt(dp.complaints)
            val ry = yAt(dp.resolved)

            // Complaint dot
            if (viewMode == ViewMode.COMPLAINTS || viewMode == ViewMode.BOTH) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, cy, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#17A2F3")
                canvas.drawCircle(x, cy, 8f, paintDot)
            }

            // Resolved dot
            if (viewMode == ViewMode.RESOLVED || viewMode == ViewMode.BOTH) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, ry, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#FF4F87")
                canvas.drawCircle(x, ry, 8f, paintDot)
            }

            // Value bubbles — only shown when there is a single data point
            val showBubble = n == 1

            if (showBubble) {
                if (viewMode == ViewMode.COMPLAINTS || viewMode == ViewMode.BOTH) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#17A2F3")
                    canvas.drawRoundRect(
                        RectF(x - bubbleR, cy - bubbleR - 22f, x + bubbleR, cy - 6f),
                        8f, 8f, paintValueBubble
                    )
                    canvas.drawText(dp.complaints.toString(), x, cy - 16f, paintValueText)
                }

                if (viewMode == ViewMode.RESOLVED || viewMode == ViewMode.BOTH) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#FF4F87")
                    canvas.drawRoundRect(
                        RectF(x - bubbleR, ry - bubbleR - 22f, x + bubbleR, ry - 6f),
                        8f, 8f, paintValueBubble
                    )
                    canvas.drawText(dp.resolved.toString(), x, ry - 16f, paintValueText)
                }
            }

            // For dense monthly points, skip some labels to avoid overlap
            val labelStep = when {
                n <= 10 -> 1
                n <= 16 -> 2
                else    -> 4
            }
            if (i % labelStep == 0 || i == n - 1) {
                canvas.drawText(dp.label, x, baseline + 36f, paintLabel)
            }
        }
    }
}