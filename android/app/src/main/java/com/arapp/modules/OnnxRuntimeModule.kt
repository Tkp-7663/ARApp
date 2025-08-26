package com.arapp.modules

import ai.onnxruntime.*
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.nio.FloatBuffer
import java.util.*
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = "OnnxRuntimeModule")
class OnnxRuntimeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "OnnxRuntimeModule"

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    @ReactMethod
    fun initializeModel(promise: Promise) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Initialize ONNX Runtime environment
                ortEnvironment = OrtEnvironment.getEnvironment()
                
                // Load YOLOv11n model from assets
                val modelBytes = reactApplicationContext.assets.open("yolov11n.onnx").readBytes()
                
                // Create session options
                val sessionOptions = OrtSession.SessionOptions().apply {
                    addCPU(true)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(4)
                }
                
                // Create ONNX session
                ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
                isModelLoaded = true
                
                withContext(Dispatchers.Main) {
                    promise.resolve(true)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    promise.reject("ERROR", "Model initialization failed: ${e.message}")
                }
            }
        }
    }

    @ReactMethod
    fun runInference(tensorData: String, promise: Promise) {
        if (!isModelLoaded || ortSession == null) {
            promise.reject("ERROR", "Model not initialized")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Decode base64 tensor data
                val decodedData = Base64.getDecoder().decode(tensorData)
                val floatBuffer = FloatBuffer.allocate(decodedData.size / 4)
                
                // Convert bytes to float array
                for (i in decodedData.indices step 4) {
                    val floatBits = ((decodedData[i].toInt() and 0xFF) shl 24) or
                                    ((decodedData[i + 1].toInt() and 0xFF) shl 16) or
                                    ((decodedData[i + 2].toInt() and 0xFF) shl 8) or
                                    (decodedData[i + 3].toInt() and 0xFF)
                    floatBuffer.put(Float.fromBits(floatBits))
                }
                
                val inputArray = floatBuffer.array()
                
                // Create input tensor (1, 3, 640, 640) for YOLOv11n
                val inputTensor = OnnxTensor.createTensor(
                    ortEnvironment!!,
                    FloatBuffer.wrap(inputArray),
                    longArrayOf(1, 3, 640, 640)
                )
                
                // Run inference
                val inputName = ortSession!!.inputNames.iterator().next()
                val results = ortSession!!.run(mapOf(inputName to inputTensor))
                
                // Process output
                val outputTensor = results[0].value as Array<Array<FloatArray>>
                val detections = processYoloOutput(outputTensor)
                
                // Convert to React Native array
                val detectionsArray = Arguments.createArray()
                for (detection in detections) {
                    val detectionArray = Arguments.createArray().apply {
                        pushDouble(detection[0].toDouble()) // x
                        pushDouble(detection[1].toDouble()) // y
                        pushDouble(detection[2].toDouble()) // width
                        pushDouble(detection[3].toDouble()) // height
                        pushDouble(detection[4].toDouble()) // confidence
                        pushDouble(detection[5].toDouble()) // class
                    }
                    detectionsArray.pushArray(detectionArray)
                }
                
                // Clean up
                inputTensor.close()
                results.forEach { it.close() }
                
                withContext(Dispatchers.Main) {
                    promise.resolve(detectionsArray)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    promise.reject("ERROR", "Inference failed: ${e.message}")
                }
            }
        }
    }

    private fun processYoloOutput(output: Array<Array<FloatArray>>): List<FloatArray> {
        val detections = mutableListOf<FloatArray>()
        val confidenceThreshold = 0.5f
        val iouThreshold = 0.4f
        
        // YOLOv11n output processing
        // Output shape: [1, 84, 8400] -> [1, num_anchors, (x,y,w,h,conf,classes...)]
        val predictions = output[0] // [84, 8400]
        
        for (i in 0 until predictions[0].size) { // 8400 predictions
            val confidence = predictions[4][i] // objectness confidence
            
            if (confidence > confidenceThreshold) {
                // Find best class
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
                        predictions[0][i], // x center
                        predictions[1][i], // y center  
                        predictions[2][i], // width
                        predictions[3][i], // height
                        finalScore,        // confidence
                        bestClass.toFloat() // class
                    ))
                }
            }
        }
        
        // Apply Non-Maximum Suppression (NMS)
        return applyNMS(detections, iouThreshold)
    }

    private fun applyNMS(detections: List<FloatArray>, iouThreshold: Float): List<FloatArray> {
        val sortedDetections = detections.sortedByDescending { it[4] } // Sort by confidence
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
        
        return keepDetections
    }

    private fun calculateIoU(box1: FloatArray, box2: FloatArray): Float {
        // Convert center coordinates to corner coordinates
        val x1_1 = box1[0] - box1[2] / 2
        val y1_1 = box1[1] - box1[3] / 2
        val x2_1 = box1[0] + box1[2] / 2
        val y2_1 = box1[1] + box1[3] / 2
        
        val x1_2 = box2[0] - box2[2] / 2
        val y1_2 = box2[1] - box2[3] / 2
        val x2_2 = box2[0] + box2[2] / 2
        val y2_2 = box2[1] + box2[3] / 2
        
        // Calculate intersection
        val xLeft = maxOf(x1_1, x1_2)
        val yTop = maxOf(y1_1, y1_2)
        val xRight = minOf(x2_1, x2_2)
        val yBottom = minOf(y2_1, y2_2)
        
        if (xRight <= xLeft || yBottom <= yTop) return 0f
        
        val intersectionArea = (xRight - xLeft) * (yBottom - yTop)
        val box1Area = (x2_1 - x1_1) * (y2_1 - y1_1)
        val box2Area = (x2_2 - x1_2) * (y2_2 - y1_2)
        val unionArea = box1Area + box2Area - intersectionArea
        
        return intersectionArea / unionArea
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
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
