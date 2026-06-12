package com.GIDC.app.dashboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.GIDC.app.dashboard.HeadDashboardActivity.ViewMode
import kotlin.math.pow

/**
 * Dot-based trend chart view.
 * X axis = date labels, Y axis = counts.
 * Supports view modes:
 *   ViewMode.COMPLAINTS — blue dots + line
 *   ViewMode.APPROVED   — pink dots + line
 *   ViewMode.CANCELED   — orange dots + line
 *   ViewMode.TOTAL      — mixed view of all three series
 *
 * Lines are drawn as STRAIGHT segments (lineTo only).
 * No cubicTo / quadTo → no curves.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DataPoint(val label: String, val complaints: Int, val approved: Int, val canceled: Int) {
        val total: Int
            get() = complaints + approved + canceled
    }

    private var dataPoints: List<DataPoint> = emptyList()
    private var viewMode: ViewMode = ViewMode.COMPLAINTS

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
    private val paintApprovedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4F87")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintCanceledLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
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
        textAlign = Paint.Align.RIGHT
    }
    private val paintAxisTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6F7F99")
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
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
        val padL = 72f
        val padR = 24f
        val padT = 30f
        val padB = 74f

        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // ── Empty state ───────────────────────────────────────────
        if (dataPoints.isEmpty()) {
            canvas.drawText("No data available", w / 2f, h / 2f, paintEmptyText)
            return
        }

        // ── Determine max value based on view mode ─────────────────
        val rawMaxVal = when (viewMode) {
            ViewMode.COMPLAINTS -> dataPoints.maxOf { it.complaints }.coerceAtLeast(1)
            ViewMode.APPROVED   -> dataPoints.maxOf { it.approved }.coerceAtLeast(1)
            ViewMode.CANCELED   -> dataPoints.maxOf { it.canceled }.coerceAtLeast(1)
            ViewMode.TOTAL      -> dataPoints.maxOf { maxOf(it.complaints, it.approved, it.canceled) }.coerceAtLeast(1)
        }
        val yStep = computeYAxisStep(rawMaxVal)
        val topAxisValue = (((rawMaxVal + yStep - 1) / yStep) * yStep).coerceAtLeast(yStep)

        val n = dataPoints.size

        // ── Grid lines ────────────────────────────────────────────
        val gridCount = (topAxisValue / yStep).coerceAtLeast(1)
        for (i in 0..gridCount) {
            val ratio = i.toFloat() / gridCount.toFloat()
            val y = padT + chartH * (1f - ratio)
            canvas.drawLine(padL, y, w - padR, y, paintGrid)
            val value = yStep * i
            canvas.drawText(value.toString(), padL - 10f, y + 8f, paintYLabel)
        }

        // ── Axis line ─────────────────────────────────────────────
        canvas.drawLine(padL, padT, padL, padT + chartH, paintAxis)
        canvas.drawLine(padL, padT + chartH, w - padR, padT + chartH, paintAxis)

        // ── Coordinate helpers ────────────────────────────────────
        fun xAt(i: Int): Float = if (n == 1)
            padL + chartW / 2f
        else
            padL + (i.toFloat() / (n - 1).toFloat()) * chartW

        fun yAt(v: Int): Float = padT + chartH * (1f - v.toFloat() / topAxisValue.toFloat())

        val baseline = padT + chartH

        // ── Build and draw STRAIGHT connecting lines ───────────────
        //
        //  ✅ CHANGED: was cubicTo(cx, pY, cx, y, x, y) — smooth Bézier curve
        //  ✅ NOW:     lineTo(x, y)                       — straight line
        //
        if (n > 1) {
            val complaintPath = Path()
            val approvedPath  = Path()
            val canceledPath  = Path()

            dataPoints.forEachIndexed { i, dp ->
                val x  = xAt(i)
                val cy = yAt(dp.complaints)
                val ay = yAt(dp.approved)
                val zy = yAt(dp.canceled)

                if (i == 0) {
                    complaintPath.moveTo(x, cy)
                    approvedPath.moveTo(x, ay)
                    canceledPath.moveTo(x, zy)
                } else {
                    complaintPath.lineTo(x, cy)   // straight segment
                    approvedPath.lineTo(x, ay)    // straight segment
                    canceledPath.lineTo(x, zy)    // straight segment
                }
            }

            if (viewMode == ViewMode.COMPLAINTS) {
                canvas.drawPath(complaintPath, paintComplaintLine)
            }
            if (viewMode == ViewMode.APPROVED) {
                canvas.drawPath(approvedPath, paintApprovedLine)
            }
            if (viewMode == ViewMode.CANCELED) {
                canvas.drawPath(canceledPath, paintCanceledLine)
            }
            if (viewMode == ViewMode.TOTAL) {
                canvas.drawPath(complaintPath, paintComplaintLine)
                canvas.drawPath(approvedPath, paintApprovedLine)
                canvas.drawPath(canceledPath, paintCanceledLine)
            }
        }

        // ── Draw dots + value bubbles ─────────────────────────────
        dataPoints.forEachIndexed { i, dp ->
            val x  = xAt(i)
            val cy = yAt(dp.complaints)
            val ay = yAt(dp.approved)
            val zy = yAt(dp.canceled)

            // Complaint dot
            if (viewMode == ViewMode.COMPLAINTS) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, cy, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#17A2F3")
                canvas.drawCircle(x, cy, 8f, paintDot)
            }

            // Approved dot
            if (viewMode == ViewMode.APPROVED) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, ay, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#FF4F87")
                canvas.drawCircle(x, ay, 8f, paintDot)
            }

            // Canceled dot
            if (viewMode == ViewMode.CANCELED) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, zy, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#F59E0B")
                canvas.drawCircle(x, zy, 8f, paintDot)
            }
            if (viewMode == ViewMode.TOTAL) {
                paintDotBorder.color = Color.WHITE
                canvas.drawCircle(x, cy, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#17A2F3")
                canvas.drawCircle(x, cy, 8f, paintDot)

                canvas.drawCircle(x, ay, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#FF4F87")
                canvas.drawCircle(x, ay, 8f, paintDot)

                canvas.drawCircle(x, zy, 11f, paintDotBorder)
                paintDot.color = Color.parseColor("#F59E0B")
                canvas.drawCircle(x, zy, 8f, paintDot)
            }

            // Value bubbles — only shown when there is a single data point
            val showBubble = n == 1

            if (showBubble) {
                if (viewMode == ViewMode.COMPLAINTS) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#17A2F3")
                    canvas.drawRoundRect(
                        RectF(x - bubbleR, cy - bubbleR - 22f, x + bubbleR, cy - 6f),
                        8f, 8f, paintValueBubble
                    )
                    canvas.drawText(dp.complaints.toString(), x, cy - 16f, paintValueText)
                }

                if (viewMode == ViewMode.APPROVED) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#FF4F87")
                    canvas.drawRoundRect(
                        RectF(x - bubbleR, ay - bubbleR - 22f, x + bubbleR, ay - 6f),
                        8f, 8f, paintValueBubble
                    )
                    canvas.drawText(dp.approved.toString(), x, ay - 16f, paintValueText)
                }

                if (viewMode == ViewMode.CANCELED) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#F59E0B")
                    canvas.drawRoundRect(
                        RectF(x - bubbleR, zy - bubbleR - 22f, x + bubbleR, zy - 6f),
                        8f, 8f, paintValueBubble
                    )
                    canvas.drawText(dp.canceled.toString(), x, zy - 16f, paintValueText)
                }
                if (viewMode == ViewMode.TOTAL) {
                    val bubbleR = 22f
                    paintValueBubble.color = Color.parseColor("#17A2F3")
                    canvas.drawRoundRect(RectF(x - bubbleR, cy - bubbleR - 22f, x + bubbleR, cy - 6f), 8f, 8f, paintValueBubble)
                    canvas.drawText(dp.complaints.toString(), x, cy - 16f, paintValueText)

                    paintValueBubble.color = Color.parseColor("#FF4F87")
                    canvas.drawRoundRect(RectF(x - bubbleR, ay - bubbleR - 22f, x + bubbleR, ay - 6f), 8f, 8f, paintValueBubble)
                    canvas.drawText(dp.approved.toString(), x, ay - 16f, paintValueText)

                    paintValueBubble.color = Color.parseColor("#F59E0B")
                    canvas.drawRoundRect(RectF(x - bubbleR, zy - bubbleR - 22f, x + bubbleR, zy - 6f), 8f, 8f, paintValueBubble)
                    canvas.drawText(dp.canceled.toString(), x, zy - 16f, paintValueText)
                }
            }

            // As date points grow, show roughly 5 evenly spaced date labels.
            val labelStep = if (n <= 5) 1 else kotlin.math.ceil(n / 5.0).toInt()
            if (i % labelStep == 0 || i == n - 1) {
                canvas.drawText(dp.label, x, baseline + 36f, paintLabel)
            }
        }

        canvas.drawText("Dates", w / 2f, h - 8f, paintAxisTitle)
        canvas.drawText("", 20f, padT + chartH / 2f, paintAxisTitle)
        canvas.drawText("Count", 36f, padT + chartH / 2f + 26f, paintAxisTitle)
    }

    private fun computeYAxisStep(maxValue: Int): Int {
        return when {
            maxValue <= 10 -> 1
            maxValue <= 100 -> 5
            maxValue <= 200 -> 10
            maxValue <= 500 -> 25
            maxValue <= 1_000 -> 50
            maxValue <= 2_000 -> 100
            maxValue <= 5_000 -> 250
            maxValue <= 10_000 -> 500
            else -> {
                val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(maxValue.toDouble())).toInt()).toInt()
                (magnitude / 2).coerceAtLeast(500)
            }
        }
    }
}