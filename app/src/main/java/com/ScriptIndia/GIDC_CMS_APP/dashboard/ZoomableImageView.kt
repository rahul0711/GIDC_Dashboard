package com.ScriptIndia.GIDC_CMS_APP.dashboard

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var matrixState = Matrix()
    private var savedMatrix = Matrix()

    private enum class State { NONE, DRAG, ZOOM }
    private var state = State.NONE

    private var start = PointF()
    private var mid = PointF()
    private var oldDist = 1f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener(this))

    private var saveScale = 1f
    private var minScale = 1f
    private var maxScale = 4f

    private var m: FloatArray = FloatArray(9)
    private var viewWidth = 0
    private var viewHeight = 0
    private var origWidth = 0f
    private var origHeight = 0f

    var onSwipeDownListener: (() -> Unit)? = null

    init {
        super.setClickable(true)
        imageMatrix = matrixState
        scaleType = ScaleType.MATRIX
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Fit to screen on start
        val drawable = drawable ?: return
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        origWidth = drawableWidth.toFloat()
        origHeight = drawableHeight.toFloat()

        val scaleX = viewWidth.toFloat() / drawableWidth.toFloat()
        val scaleY = viewHeight.toFloat() / drawableHeight.toFloat()
        val scale = minOf(scaleX, scaleY)

        matrixState.setScale(scale, scale)

        // Center the image
        val redundantYSpace = viewHeight.toFloat() - (scale * drawableHeight.toFloat())
        val redundantXSpace = viewWidth.toFloat() - (scale * drawableWidth.toFloat())

        matrixState.postTranslate(redundantXSpace / 2, redundantYSpace / 2)

        saveScale = 1f
        imageMatrix = matrixState
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            state = State.ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
            }

            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                matrixState.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f)
            } else {
                matrixState.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            }

            fixTrans()
            return true
        }
    }

    private class GestureListener(private val view: ZoomableImageView) : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null && view.saveScale == 1f) {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffY) > Math.abs(diffX) && diffY > 100 && Math.abs(velocityY) > 100) {
                    view.onSwipeDownListener?.invoke()
                    return true
                }
            }
            return false
        }
    }



    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val curr = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrixState)
                start.set(curr)
                state = State.DRAG
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.DRAG) {
                    val deltaX = curr.x - start.x
                    val deltaY = curr.y - start.y
                    
                    // If scale is 1, let drag down initiate swipe down dismiss
                    if (saveScale == 1f && deltaY > 150) {
                        onSwipeDownListener?.invoke()
                        return true
                    }
                    
                    matrixState.set(savedMatrix)
                    matrixState.postTranslate(deltaX, deltaY)
                    fixTrans()
                }
            }
            MotionEvent.ACTION_UP -> {
                state = State.NONE
            }
            MotionEvent.ACTION_POINTER_UP -> {
                state = State.NONE
            }
        }

        imageMatrix = matrixState
        invalidate()
        return true
    }

    private fun fixTrans() {
        matrixState.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]

        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), origHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrixState.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }
}
