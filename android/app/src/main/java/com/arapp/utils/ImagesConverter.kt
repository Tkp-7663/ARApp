package com.arapp.utils

import android.graphics.*
import android.media.Image
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*

class ImagesConverter {

    companion object {
        private const val INPUT_SIZE = 640
        private const val MEAN = 0.0f
        private const val STD = 255.0f
    }

    /**
     * Convert YUV Image to normalized tensor data for YOLOv11n
     * Input: ARCore camera image (YUV format)
     * Output: Base64 encoded tensor data [1, 3, 640, 640]
     */
    fun convertYuvToTensor(image: Image): String {
        try {
            // Convert YUV to RGB bitmap
            val bitmap = yuvToRgbBitmap(image)
            
            // Resize to YOLO input size (640x640)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            
            // Convert to normalized float array
            val tensorData = bitmapToNormalizedFloatArray(resizedBitmap)
            
            // Convert float array to byte array
            val byteBuffer = ByteBuffer.allocate(tensorData.size * 4)
            for (value in tensorData) {
                byteBuffer.putFloat(value)
            }
            
            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array())
            
        } catch (e: Exception) {
            throw RuntimeException("Image conversion failed: ${e.message}", e)
        }
    }

    /**
     * Convert YUV Image to RGB Bitmap
     */
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
            // Interleaved UV plane
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
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Convert bitmap to normalized float array for YOLO
     * Format: [1, 3, 640, 640] - CHW format (Channel, Height, Width)
     */
    private fun bitmapToNormalizedFloatArray(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        val tensorData = FloatArray(1 * 3 * INPUT_SIZE * INPUT_SIZE)
        
        // Convert RGB pixels to CHW format and normalize [0, 255] -> [0, 1]
        for (i in pixels.indices) {
            val pixel = pixels[i]
            
            val r = ((pixel shr 16) and 0xFF) / STD
            val g = ((pixel shr 8) and 0xFF) / STD
            val b = (pixel and 0xFF) / STD
            
            // CHW format: all R values, then all G values, then all B values
            tensorData[i] = r                                    // R channel
            tensorData[i + INPUT_SIZE * INPUT_SIZE] = g          // G channel  
            tensorData[i + 2 * INPUT_SIZE * INPUT_SIZE] = b      // B channel
        }
        
        return tensorData
    }

    /**
     * Resize image maintaining aspect ratio with padding
     */
    private fun resizeWithPadding(bitmap: Bitmap, targetSize: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        val scale = minOf(
            targetSize.toFloat() / originalWidth,
            targetSize.toFloat() / originalHeight
        )
        
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // Create padded bitmap
        val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        
        // Fill with gray color (128, 128, 128)
        canvas.drawColor(Color.rgb(128, 128, 128))
        
        // Draw scaled image centered
        val left = (targetSize - scaledWidth) / 2
        val top = (targetSize - scaledHeight) / 2
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
        
        return paddedBitmap
    }

    /**
     * Apply letterboxing for non-square images
     */
    fun calculateLetterboxParams(originalWidth: Int, originalHeight: Int, targetSize: Int): LetterboxParams {
        val scale = minOf(
            targetSize.toFloat() / originalWidth,
            targetSize.toFloat() / originalHeight
        )
        
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        val padX = (targetSize - scaledWidth) / 2
        val padY = (targetSize - scaledHeight) / 2
        
        return LetterboxParams(scale, padX, padY, scaledWidth, scaledHeight)
    }

    data class LetterboxParams(
        val scale: Float,
        val padX: Int,
        val padY: Int,
        val scaledWidth: Int,
        val scaledHeight: Int
    )
}