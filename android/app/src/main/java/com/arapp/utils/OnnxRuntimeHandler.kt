package com.arapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import com.google.ar.core.Frame
import java.nio.FloatBuffer
import android.util.Log

class OnnxRuntimeHandler(private val context: Context) {

    companion object {
        const val MODEL = "yolov11n.onnx"
        const val INPUT_SIZE = 320
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy { createSession() }

    // Load .onnx from assets
    private fun createSession(): OrtSession {
        val modelFile = File(context.filesDir, MODEL)
        if (!modelFile.exists()) {
            context.assets.open(MODEL).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    // ARCore Frame -> FloatArray Tensor
    fun convertYUVToTensor(frame: Frame): FloatArray {
        var image: Image? = null
        return try {
            image = frame.acquireCameraImage()

            if (image.format == android.graphics.ImageFormat.YUV_420_888) {
                Log.d("ARDebug", "Image format is YUV_420_888 - CORRECT FORMAT!")
            } else {
                Log.e("ARDebug", "Wrong image format: ${image.format}")
            }

            // แปลง YUV -> RGB array
            val rgb = convertYUVToRGB(image)
            Log.d("ARDebug", "RGB array created, size: ${rgb.size}")

            // แปลง RGB -> Bitmap
            val bitmap = convertRGBToBitmap(rgb, image.width, image.height)
            Log.d("ARDebug", "Bitmap created, size: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

            // Resize bitmap
            val resized = resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE)
            Log.d("ARDebug", "Bitmap resized to: ${resized.width}x${resized.height}")

            // Convert bitmap -> tensor
            val tensor = convertBitmapToTensor(resized)
            Log.d("ARDebug", "Tensor created, length: ${tensor.size}")

            // Normalize tensor
            normalizeTensor(tensor)

        } catch (e: Exception) {
            Log.e("ARDebug", "Error processing image: ", e)
            // คืนค่า default tensor ถ้าเกิด error (all zeros)
            FloatArray(3 * INPUT_SIZE * INPUT_SIZE) { 0f }
        } finally {
            image?.close()
            Log.v("ARDebug", "Image closed successfully")
        }
    }

    // YUV_420_888 -> RGB
    private fun convertYUVToRGB(image: Image): IntArray {
        val width = image.width
        val height = image.height
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val rgbArray = IntArray(width * height)

        val y = ByteArray(yBuffer.remaining())
        val u = ByteArray(uBuffer.remaining())
        val v = ByteArray(vBuffer.remaining())
        yBuffer.get(y)
        uBuffer.get(u)
        vBuffer.get(v)

        var yp: Int
        for (j in 0 until height) {
            val pY = yRowStride * j
            val uvRow = uvRowStride * (j shr 1)

            for (i in 0 until width) {
                yp = pY + i
                val uvOffset = uvRow + (i shr 1) * uvPixelStride

                val yVal = (y[yp].toInt() and 0xff) - 16
                val uVal = (u[uvOffset].toInt() and 0xff) - 128
                val vVal = (v[uvOffset].toInt() and 0xff) - 128

                var r = (1.164f * yVal + 1.596f * vVal).toInt()
                var g = (1.164f * yVal - 0.813f * vVal - 0.391f * uVal).toInt()
                var b = (1.164f * yVal + 2.018f * uVal).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                rgbArray[j * width + i] = Color.rgb(r, g, b)
            }
        }
        return rgbArray
    }

    // RGB array -> Bitmap
    private fun convertRGBToBitmap(rgb: IntArray, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888)
    }

    // Resize bitmap
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // Bitmap -> Tensor (FloatArray)
    private fun convertBitmapToTensor(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(3 * width * height)

        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                floatArray[idx] = Color.red(pixel).toFloat()
                floatArray[idx + width * height] = Color.green(pixel).toFloat()
                floatArray[idx + 2 * width * height] = Color.blue(pixel).toFloat()
                idx++
            }
        }
        return floatArray
    }

    // Normalize tensor
    private fun normalizeTensor(tensor: FloatArray): FloatArray {
        return tensor.map { it / 255f }.toFloatArray()
    }

    data class Detection(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val confidence: Float
    )

    // Run inference with ONNX Runtime
    fun runOnnxInference(tensor: FloatArray): List<Detection> {
        Log.d("ARDebug", "Preparing input tensor for ONNX, shape = [1, 3, $INPUT_SIZE, $INPUT_SIZE]")
        
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val floatBuffer = java.nio.FloatBuffer.wrap(tensor)
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
        Log.d("ARDebug", "Input tensor created successfully")

        val result = session.run(mapOf("images" to inputTensor))

        val output = result[0].value
        Log.d("ARDebug", "Raw output type: ${output!!::class}, output = $output")

        if (output is Array<*>) {
            // output = [[[...]]]  shape (1,5,2100)
            val arr = output as Array<Array<FloatArray>>
            val detections = mutableListOf<Detection>()
            val boxes = arr[0]   // shape (5,2100)
            Log.d("ARDebug", "Processing output array, boxes shape: [${boxes.size}, ${boxes[0].size}]")

            for (i in 0 until boxes[0].size) {
                val x = boxes[0][i]
                val y = boxes[1][i]
                val w = boxes[2][i]
                val h = boxes[3][i]
                val conf = boxes[4][i]

                if (conf > 0.1f) { // threshold
                    detections.add(Detection(x, y, w, h, conf))
                    Log.d("ARDebug", "Detection added: x=$x, y=$y, w=$w, h=$h, conf=$conf")
                } else {
                    Log.d("ARDebug", "Detection skipped (conf too low): x=$x, y=$y, w=$w, h=$h, conf=$conf")
                }
            }

            Log.d("ARDebug", "Total valid detections: ${detections.size}")
            return detections

        } else {
            Log.e("ARDebug", "Unexpected output type: ${output!!::class}")
            throw IllegalArgumentException("Unexpected output type: ${output!!::class}")
        }
    }

    // ปิด resource เมื่อ Activity/Service ถูกทำลาย
    fun close() {
        try {
            session.close()
            env.close()
            Log.d("OnnxRuntimeHandler", "ONNX resources closed")
        } catch (e: Exception) {
            Log.e("OnnxRuntimeHandler", "Error closing resources", e)
        }
    }
}
