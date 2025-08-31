package com.arapp.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import io.github.sceneview.ar.ARSceneView

class OverlayView(context: Context, private val sceneView: ARSceneView) : View(context) {

    var detections: FloatArray = floatArrayOf()
    var modelInputSize: Int = 320
    var confidenceThreshold: Float = 0.5f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoundingBoxes(canvas)
    }

    private fun drawBoundingBoxes(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()
        val scaleX = screenWidth / modelInputSize
        val scaleY = screenHeight / modelInputSize

        val step = 5
        for (i in 0 until detections.size step step) {
            val xCenter = detections[i] * scaleX
            val yCenter = detections[i + 1] * scaleY
            val wPixel = detections[i + 2] * scaleX
            val hPixel = detections[i + 3] * scaleY
            val confidence = detections[i + 4]

            if (confidence < confidenceThreshold) continue

            val left = xCenter - wPixel / 2f
            val top = yCenter - hPixel / 2f
            val right = xCenter + wPixel / 2f
            val bottom = yCenter + hPixel / 2f

            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
