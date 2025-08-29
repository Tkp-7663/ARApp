package com.arapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream

class OnnxRuntimeHandler(private val context: Context) {

    companion object {
        const val MODEL = "yolov11n_fp16.onnx"
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
    fun convertYUVToTensor(frame: Image): FloatArray {
        val rgb = convertYUVToRGB(frame)
        val bitmap = convertRGBToBitmap(rgb, frame.width, frame.height)
        val resized = resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE)
        val tensor = convertBitmapToTensor(resized)
        return normalizeTensor(tensor)
    }

    // YUV_420_888 -> RGB
    private fun convertYUVToRGB(frame: Image): IntArray {
        val width = frame.width
        val height = frame.height
        val yBuffer = frame.planes[0].buffer
        val uBuffer = frame.planes[1].buffer
        val vBuffer = frame.planes[2].buffer

        val yRowStride = frame.planes[0].rowStride
        val uvRowStride = frame.planes[1].rowStride
        val uvPixelStride = frame.planes[1].pixelStride

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
                floatArray[idx] = (Color.red(pixel).toFloat())
                floatArray[idx + width * height] = (Color.green(pixel).toFloat())
                floatArray[idx + 2 * width * height] = (Color.blue(pixel).toFloat())
                idx++
            }
        }
        return floatArray
    }

    // Normalize tensor
    private fun normalizeTensor(tensor: FloatArray): FloatArray {
        return tensor.map { it / 255f }.toFloatArray()
    }

    // Run inference with ONNX Runtime
    fun runOnnxInference(tensor: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputTensor = OnnxTensor.createTensor(env, tensor, shape)

        session.use { sess ->
            val result = sess.run(mapOf(sess.inputNames.iterator().next() to inputTensor))
            val output = result[0].value as Array<FloatArray>
            return output[0] // สมมติว่า output เป็น [1, N]

            // val output = result[0].value
            // println("Output class = ${output::class.java}")
            // println("Output value = ${output}")

        }
    }
}
