package com.arapp.modules

import ai.onnxruntime.*
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.nio.FloatBuffer
import com.facebook.react.module.annotations.ReactModule
import android.util.Log
import java.io.IOException

@ReactModule(name = "OnnxRuntimeModule")
class OnnxRuntimeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "OnnxRuntimeModule"

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "OnnxRuntimeModule"
        private const val MODEL_INPUT_SIZE = 320
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val IOU_THRESHOLD = 0.4f
    }

    @ReactMethod
    fun initializeModel(promise: Promise) {
        Log.d(TAG, "Starting model initialization...")
        
        moduleScope.launch {
            try {
                // สร้าง ORT Environment
                ortEnvironment = OrtEnvironment.getEnvironment()
                Log.d(TAG, "ORT Environment created")

                // อ่าน model file
                val modelInputStream = try {
                    reactApplicationContext.assets.open("yolov11n_fp16.ort")
                } catch (e: IOException) {
                    Log.e(TAG, "Model file not found: yolov11n_fp16.ort", e)
                    throw Exception("Model file not found: yolov11n_fp16.ort. Make sure the file is in assets folder.")
                }

                val modelBytes = modelInputStream.use { it.readBytes() }
                Log.d(TAG, "Model file loaded, size: ${modelBytes.size} bytes")

                // สร้าง session options
                val sessionOptions = OrtSession.SessionOptions().apply {
                    addCPU(true) // ใช้ CPU provider
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(4) // ลดจำนวน threads
                }

                // สร้าง ONNX session
                ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
                Log.d(TAG, "ORT Session created successfully")

                // ตรวจสอบ input/output info
                val inputInfo = ortSession!!.inputInfo
                val outputInfo = ortSession!!.outputInfo
                
                Log.d(TAG, "Model inputs: ${inputInfo.keys}")
                Log.d(TAG, "Model outputs: ${outputInfo.keys}")
                
                inputInfo.forEach { (name, info) ->
                    Log.d(TAG, "Input '$name': ${info.info}")
                }
                
                outputInfo.forEach { (name, info) ->
                    Log.d(TAG, "Output '$name': ${info.info}")
                }

                isModelLoaded = true
                
                withContext(Dispatchers.Main) { 
                    Log.d(TAG, "Model initialization completed successfully")
                    promise.resolve(true) 
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Model initialization failed", e)
                withContext(Dispatchers.Main) { 
                    promise.reject("ONNX_INIT_ERROR", "Model initialization failed: ${e.message}")
                }
            }
        }
    }

    @ReactMethod
    fun runInferenceFromFrame(frameDataMap: ReadableMap, promise: Promise) {
        Log.d(TAG, "runInferenceFromFrame called")
        
        if (!isModelLoaded) {
            Log.e(TAG, "Model not loaded")
            promise.reject("MODEL_NOT_LOADED", "Model not loaded")
            return
        }

        moduleScope.launch {
            var inputTensor: OnnxTensor? = null
            var results: OrtSession.Result? = null
            
            try {
                // แปลงข้อมูลจาก ReadableMap เป็น FloatBuffer
                val tensorBuffer = convertFrameDataToTensor(frameDataMap)
                Log.d(TAG, "Tensor buffer created, capacity: ${tensorBuffer.capacity()}")

                // สร้าง ONNX tensor
                val shape = longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
                inputTensor = OnnxTensor.createTensor(ortEnvironment, tensorBuffer, shape)
                Log.d(TAG, "ONNX tensor created with shape: ${shape.contentToString()}")

                // เตรียม inputs
                val inputs = mapOf("images" to inputTensor)
                
                // รัน inference
                Log.d(TAG, "Running inference...")
                results = ortSession!!.run(inputs)
                Log.d(TAG, "Inference completed")

                // ประมวลผล output
                val outputValue = results[0].value
                Log.d(TAG, "Output type: ${outputValue::class.java.simpleName}")
                
                val detections = when (outputValue) {
                    is Array<*> -> {
                        Log.d(TAG, "Processing array output")
                        processYoloOutput(outputValue as Array<Array<FloatArray>>)
                    }
                    is FloatArray -> {
                        Log.d(TAG, "Processing float array output")
                        processYoloOutputFlat(outputValue)
                    }
                    else -> {
                        Log.w(TAG, "Unknown output format: ${outputValue::class.java}")
                        emptyList<FloatArray>()
                    }
                }

                Log.d(TAG, "Found ${detections.size} detections")

                // ส่งกลับไป JavaScript
                withContext(Dispatchers.Main) {
                    val array = Arguments.createArray()
                    detections.forEach { detection ->
                        val detArray = Arguments.createArray()
                        detection.forEach { value -> 
                            detArray.pushDouble(value.toDouble()) 
                        }
                        array.pushArray(detArray)
                    }
                    promise.resolve(array)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Inference failed", e)
                withContext(Dispatchers.Main) {
                    promise.reject("INFERENCE_ERROR", "Inference failed: ${e.message}")
                }
            } finally {
                try { 
                    inputTensor?.close() 
                    Log.d(TAG, "Input tensor closed")
                } catch (e: Exception) { 
                    Log.w(TAG, "Failed to close input tensor", e)
                }
                try { 
                    results?.close() 
                    Log.d(TAG, "Results closed")
                } catch (e: Exception) { 
                    Log.w(TAG, "Failed to close results", e)
                }
            }
        }
    }

    private fun convertFrameDataToTensor(frameDataMap: ReadableMap): FloatBuffer {
        // สมมติว่าเราได้รับข้อมูล RGB pixels จาก JavaScript
        val pixelsArray = frameDataMap.getArray("pixels")
        val width = frameDataMap.getInt("width")
        val height = frameDataMap.getInt("height")
        
        Log.d(TAG, "Frame data: ${width}x${height}, pixels: ${pixelsArray?.size()}")

        val tensorSize = 3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE
        val buffer = FloatBuffer.allocate(tensorSize)

        if (pixelsArray != null) {
            // แปลง pixels เป็น normalized tensor
            val scaleX = width.toFloat() / MODEL_INPUT_SIZE
            val scaleY = height.toFloat() / MODEL_INPUT_SIZE

            for (y in 0 until MODEL_INPUT_SIZE) {
                for (x in 0 until MODEL_INPUT_SIZE) {
                    val srcX = (x * scaleX).toInt().coerceIn(0, width - 1)
                    val srcY = (y * scaleY).toInt().coerceIn(0, height - 1)
                    val pixelIndex = srcY * width + srcX
                    
                    if (pixelIndex < pixelsArray.size()) {
                        val pixel = pixelsArray.getInt(pixelIndex)
                        val r = ((pixel shr 16) and 0xFF) / 255.0f
                        val g = ((pixel shr 8) and 0xFF) / 255.0f
                        val b = (pixel and 0xFF) / 255.0f
                        
                        // ONNX format: CHW (Channel, Height, Width)
                        val baseIndex = y * MODEL_INPUT_SIZE + x
                        buffer.put(baseIndex, r) // R channel
                        buffer.put(baseIndex + MODEL_INPUT_SIZE * MODEL_INPUT_SIZE, g) // G channel
                        buffer.put(baseIndex + 2 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE, b) // B channel
                    }
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun processYoloOutput(output: Array<Array<FloatArray>>): List<FloatArray> {
        Log.d(TAG, "Processing YOLO output array format")
        val detections = mutableListOf<FloatArray>()
        
        if (output.isEmpty() || output[0].isEmpty()) {
            Log.w(TAG, "Empty output array")
            return detections
        }

        val predictions = output[0]
        Log.d(TAG, "Predictions shape: ${predictions.size} x ${predictions[0].size}")

        for (i in 0 until predictions[0].size) {
            if (predictions.size < 6) continue
            
            val confidence = predictions[4][i]
            if (confidence > CONFIDENCE_THRESHOLD) {
                var bestClass = 0
                var bestScore = if (predictions.size > 5) predictions[5][i] else confidence
                
                for (j in 5 until predictions.size) {
                    if (predictions[j][i] > bestScore) {
                        bestScore = predictions[j][i]
                        bestClass = j - 5
                    }
                }
                
                val finalScore = confidence * bestScore
                if (finalScore > CONFIDENCE_THRESHOLD) {
                    detections.add(floatArrayOf(
                        predictions[0][i], // x
                        predictions[1][i], // y
                        predictions[2][i], // w
                        predictions[3][i], // h
                        finalScore,        // confidence
                        bestClass.toFloat() // class
                    ))
                }
            }
        }

        return applyNMS(detections, IOU_THRESHOLD)
    }

    private fun processYoloOutputFlat(output: FloatArray): List<FloatArray> {
        Log.d(TAG, "Processing YOLO output flat format, size: ${output.size}")
        val detections = mutableListOf<FloatArray>()
        
        // YOLOv11n output format: [1, 84, 8400] -> flattened
        val numClasses = 80 // COCO classes
        val numPredictions = output.size / (4 + 1 + numClasses) // x,y,w,h + confidence + classes
        
        Log.d(TAG, "Estimated predictions: $numPredictions")
        
        for (i in 0 until numPredictions) {
            val baseIndex = i * (4 + 1 + numClasses)
            if (baseIndex + 4 + numClasses >= output.size) break
            
            val x = output[baseIndex]
            val y = output[baseIndex + 1]
            val w = output[baseIndex + 2]
            val h = output[baseIndex + 3]
            val confidence = output[baseIndex + 4]
            
            if (confidence > CONFIDENCE_THRESHOLD) {
                var bestClass = 0
                var bestScore = 0f
                
                for (j in 0 until numClasses) {
                    val classScore = output[baseIndex + 5 + j]
                    if (classScore > bestScore) {
                        bestScore = classScore
                        bestClass = j
                    }
                }
                
                val finalScore = confidence * bestScore
                if (finalScore > CONFIDENCE_THRESHOLD) {
                    detections.add(floatArrayOf(x, y, w, h, finalScore, bestClass.toFloat()))
                }
            }
        }
        
        return applyNMS(detections, IOU_THRESHOLD)
    }

    private fun applyNMS(detections: List<FloatArray>, iouThreshold: Float): List<FloatArray> {
        if (detections.isEmpty()) return detections
        
        val sortedDetections = detections.sortedByDescending { it[4] }
        val keepDetections = mutableListOf<FloatArray>()
        val suppressed = BooleanArray(sortedDetections.size) { false }

        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            keepDetections.add(sortedDetections[i])
            
            for (j in i + 1 until sortedDetections.size) {
                if (suppressed[j]) continue
                val iou = calculateIoU(sortedDetections[i], sortedDetections[j])
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        Log.d(TAG, "NMS: ${detections.size} -> ${keepDetections.size} detections")
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
        
        return intersectionArea / (box1Area + box2Area - intersectionArea + 1e-7f)
    }

    @ReactMethod
    fun checkModelStatus(promise: Promise) {
        val status = WritableNativeMap().apply {
            putBoolean("isLoaded", isModelLoaded)
            putBoolean("hasEnvironment", ortEnvironment != null)
            putBoolean("hasSession", ortSession != null)
        }
        promise.resolve(status)
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.d(TAG, "Destroying ONNX module")
        
        moduleScope.cancel()
        
        try {
            ortSession?.close()
            ortSession = null
            Log.d(TAG, "ORT Session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ORT Session", e)
        }
        
        try {
            ortEnvironment?.close()
            ortEnvironment = null
            Log.d(TAG, "ORT Environment closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ORT Environment", e)
        }
        
        isModelLoaded = false
    }
}