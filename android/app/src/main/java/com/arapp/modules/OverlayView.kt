package com.arapp.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import io.github.sceneview.ar.ARSceneView
import android.util.Log

data class Detection(
    val xCenter: Float,
    val yCenter: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val className: String = "wheel"     // label ของ object
)

class OverlayView(context: Context, private val sceneView: ARSceneView) : View(context) {

    var detections: List<Detection> = emptyList()
    var modelInputSize: Int = 320
    var confidenceThreshold: Float = 0.3f

    private val boxPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.BLUE
        textSize = 32f
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("ARDebug", "onDraw called. Canvas size: ${canvas.width}x${canvas.height}, total detections: ${detections.size}")
        drawBoundingBoxes(canvas)
    }

    private fun drawBoundingBoxes(canvas: Canvas) {
        val scaleX = width.toFloat() / modelInputSize
        val scaleY = height.toFloat() / modelInputSize
        Log.d("ARDebug", "Drawing bounding boxes with scaleX=$scaleX, scaleY=$scaleY")

        detections.forEachIndexed { index, det ->
            if (det.confidence < confidenceThreshold) {
                Log.d("ARDebug", "Detection #$index skipped due to confidence ${det.confidence} < $confidenceThreshold")
                return@forEachIndexed
            }

            // แก้ไขการคำนวณ bounding box
            // วิธีที่ 4: inverse เฉพาะ Y (top/bottom)
            var left = (modelInputSize - (det.yCenter + det.height / 2f)) * scaleX
            var top = (det.xCenter - det.width / 2f) * scaleY
            var right = (modelInputSize - (det.yCenter - det.height / 2f)) * scaleX
            var bottom = (det.xCenter + det.width / 2f) * scaleY

            // ตรวจสอบและแก้ไขถ้า left > right หรือ top > bottom
            if (left > right) {
                val temp = left
                left = right
                right = temp
            }
            if (top > bottom) {
                val temp = top
                top = bottom
                bottom = temp
            }

            // Clipping ให้อยู่ในขอบเขต canvas
            left = left.coerceIn(0f, width.toFloat())
            top = top.coerceIn(0f, height.toFloat())
            right = right.coerceIn(0f, width.toFloat())
            bottom = bottom.coerceIn(0f, height.toFloat())

            Log.d("ARDebug", "Detection #$index bounding box: left=$left, top=$top, right=$right, bottom=$bottom, class=${det.className}, conf=${det.confidence}")
            Log.d("ARDebug", "Canvas bounds: width=$width, height=$height")

            // วาดกล่องเฉพาะถ้ามีขนาด
            if (right > left && bottom > top) {
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // วาด label + confidence
                val label = "${det.className} ${"%.2f".format(det.confidence)}"
                canvas.drawText(label, left, maxOf(top - 8f, 32f), textPaint)
            } else {
                Log.d("ARDebug", "Detection #$index skipped - invalid rectangle dimensions")
            }
        }
    }
}