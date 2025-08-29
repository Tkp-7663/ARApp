package com.arapp.utils

import android.graphics.*
import android.media.Image
import java.nio.FloatBuffer

object ImagesConverter {

    const val INPUT_SIZE = 320
    private const val STD = 255.0f

    /**
     * Convert YUV Image to normalized tensor data for YOLOv11n
     * Output: FloatBuffer [1, 3, 320, 320]
     */
    fun convertYuvToTensor(image: Image): FloatBuffer {
        val bitmap = yuvToRgbBitmap(image)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        return bitmapToNormalizedFloatBuffer(resizedBitmap)
    }

    /**
     * Convert YUV Image to IntArray [0xRRGGBB]
     */
    fun yuvToRgb(image: Image): IntArray {
        val bitmap = yuvToRgbBitmap(image)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun yuvToRgbBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.buffer.get(nv21, 0, ySize)

        val uvPixelStride = uPlane.pixelStride
        if (uvPixelStride == 1) {
            uPlane.buffer.get(nv21, ySize, uSize)
            vPlane.buffer.get(nv21, ySize + uSize, vSize)
        } else {
            val uvBuffer = uPlane.buffer
            val vvBuffer = vPlane.buffer
            var pos = ySize
            for (i in 0 until uSize) {
                nv21[pos] = uvBuffer.get()
                nv21[pos + 1] = vvBuffer.get()
                pos += 2
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun bitmapToNormalizedFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / STD
            val g = ((pixel shr 8) and 0xFF) / STD
            val b = (pixel and 0xFF) / STD

            buffer.put(r)  // R
            buffer.put(g)  // G
            buffer.put(b)  // B
        }
        buffer.rewind()
        return buffer
    }
}
