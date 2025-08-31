package com.arapp.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import io.github.sceneview.ar.ARSceneView

data class Detection(
    val xCenter: Float,
    val yCenter: Float,
    val width: Float,
    val height: Float,
    val confidence: Float
)

class OverlayView(context: Context, private val sceneView: ARSceneView) : View(context) {

    var detections: List<Detection> = emptyList()
    var modelInputSize: Int = 320
    var confidenceThreshold: Float = 0.5f

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoundingBoxes(canvas)
    }

    private fun drawBoundingBoxes(canvas: Canvas) {
        val scaleX = width.toFloat() / modelInputSize
        val scaleY = height.toFloat() / modelInputSize

        detections.forEach { det ->
            if (det.confidence < confidenceThreshold) return@forEach

            val left = (det.xCenter - det.width / 2f) * scaleX
            val top = (det.yCenter - det.height / 2f) * scaleY
            val right = (det.xCenter + det.width / 2f) * scaleX
            val bottom = (det.yCenter + det.height / 2f) * scaleY

            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
