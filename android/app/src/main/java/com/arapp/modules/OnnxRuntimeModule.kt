package com.arapp.modules

import ai.onnxruntime.*
import android.media.Image
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.nio.FloatBuffer
import com.facebook.react.module.annotations.ReactModule
import com.arapp.utils.ImagesConverter

@ReactModule(name = "OnnxRuntimeModule")
class OnnxRuntimeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "OnnxRuntimeModule"

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @ReactMethod
    fun initializeModel(promise: Promise) {
        moduleScope.launch {
            try {
                ortEnvironment = OrtEnvironment.getEnvironment()

                val modelBytes = reactApplicationContext.assets.open("yolov11n.onnx").readBytes()

                val sessionOptions = OrtSession.SessionOptions().apply {
                    addCPU(true)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(4)
                }

                ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
                isModelLoaded = true

                withContext(Dispatchers.Main) { promise.resolve(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { promise.reject("ERROR", "Model initialization failed: ${e.message}") }
            }
        }
    }

    // รับ YUV frame จาก SceneViewModule เป็น argument
    @ReactMethod
    fun runInferenceFromFrame(frame: Image, promise: Promise) {
        if (!isModelLoaded) {
            promise.reject("ERROR", "Model not loaded")
            return
        }

        moduleScope.launch {
            var inputTensor: OnnxTensor? = null
            var results: OrtSession.Result? = null
            try {
                // แปลง YUV → Tensor
                val tensorBuffer: FloatBuffer = ImagesConverter.convertYuvToTensor(frame)

                // สร้าง ONNX tensor
                val shape = longArrayOf(1, 3, 320, 320)
                inputTensor = OnnxTensor.createTensor(ortEnvironment, tensorBuffer, shape)

                val inputs = mapOf("images" to inputTensor)
                results = ortSession!!.run(inputs) // run inference

                // สมมติ output อยู่ index 0
                val rawOutput = results[0].value as Array<Array<FloatArray>>
                val detections = processYoloOutput(rawOutput)

                // ส่งกลับไป JS
                withContext(Dispatchers.Main) {
                    val array = Arguments.createArray()
                    detections.forEach { det ->
                        val detArray = Arguments.createArray()
                        det.forEach { detArray.pushDouble(it.toDouble()) }
                        array.pushArray(detArray)
                    }
                    promise.resolve(array)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    promise.reject("ERROR", "Inference failed: ${e.message}")
                }
            } finally {
                try { inputTensor?.close() } catch (_: Exception) {}
                try { results?.it.close() } catch (_: Exception) {}
            }
        }
    }

    private fun processYoloOutput(output: Array<Array<FloatArray>>): List<FloatArray> {
        val detections = mutableListOf<FloatArray>()
        val confidenceThreshold = 0.5f
        val iouThreshold = 0.4f

        val predictions = output[0]

        for (i in 0 until predictions[0].size) {
            val confidence = predictions[4][i]
            if (confidence > confidenceThreshold) {
                var bestClass = 0
                var bestScore = predictions[5][i]
                for (j in 6 until predictions.size) {
                    if (predictions[j][i] > bestScore) {
                        bestScore = predictions[j][i]
                        bestClass = j - 5
                    }
                }
                val finalScore = confidence * bestScore
                if (finalScore > confidenceThreshold) {
                    detections.add(floatArrayOf(
                        predictions[0][i],
                        predictions[1][i],
                        predictions[2][i],
                        predictions[3][i],
                        finalScore,
                        bestClass.toFloat()
                    ))
                }
            }
        }

        return applyNMS(detections, iouThreshold)
    }

    private fun applyNMS(detections: List<FloatArray>, iouThreshold: Float): List<FloatArray> {
        val sortedDetections = detections.sortedByDescending { it[4] }
        val keepDetections = mutableListOf<FloatArray>()
        val suppressed = BooleanArray(sortedDetections.size) { false }

        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            keepDetections.add(sortedDetections[i])
            for (j in i + 1 until sortedDetections.size) {
                if (suppressed[j]) continue
                val iou = calculateIoU(sortedDetections[i], sortedDetections[j])
                if (iou > iouThreshold) suppressed[j] = true
            }
        }

        return keepDetections
    }

    private fun calculateIoU(box1: FloatArray, box2: FloatArray): Float {
        val x1_1 = box1[0] - box1[2] / 2
        val y1_1 = box1[1] - box1[3] / 2
        val x2_1 = box1[0] + box1[2] / 2
        val y2_1 = box1[1] + box1[3] / 2
        val x1_2 = box2[0] - box2[2] / 2
        val y1_2 = box2[1] - box2[3] / 2
        val x2_2 = box2[0] + box2[2] / 2
        val y2_2 = box2[1] + box2[3] / 2

        val xLeft = maxOf(x1_1, x1_2)
        val yTop = maxOf(y1_1, y1_2)
        val xRight = minOf(x2_1, x2_2)
        val yBottom = minOf(y2_1, y2_2)

        if (xRight <= xLeft || yBottom <= yTop) return 0f

        val intersectionArea = (xRight - xLeft) * (yBottom - yTop)
        val box1Area = (x2_1 - x1_1) * (y2_1 - y1_1)
        val box2Area = (x2_2 - x1_2) * (y2_2 - y1_2)
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        moduleScope.cancel()
        try {
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            isModelLoaded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
