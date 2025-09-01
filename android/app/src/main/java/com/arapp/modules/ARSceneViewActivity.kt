package com.arapp.modules

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import com.arapp.utils.OnnxRuntimeHandler
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.Config


class ARSceneViewActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var onnxHandler: OnnxRuntimeHandler
    private lateinit var overlayView: OverlayView
    private lateinit var arRenderer: ARRenderer
    private var session: Session? = null
    private var isARSessionStarted = false

    private var lastFrameTime = 0L
    private val frameIntervalNs = 1_000_000_000L / 30L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ตรวจสอบ camera permission ก่อน
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ARDebug", "Camera permission not granted")
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d("ARDebug", "Camera permission granted, setting up views...")

        try {
            setupViews()
        } catch (e: Exception) {
            Log.e("ARDebug", "Error in onCreate", e)
            Toast.makeText(this, "Failed to initialize AR", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupViews() {
        Log.d("ARDebug", "setupViews started")
        try {
            onnxHandler = OnnxRuntimeHandler(applicationContext)
            Log.d("ARDebug", "ONNX handler initialized")
        } catch (e: Exception) {
            Log.e("ARDebug", "Failed to initialize ONNX handler", e)
            throw e
        }

        try {
            arRenderer = ARRenderer()
            Log.d("ARDebug", "AR Renderer initialized")
        } catch (e: Exception) {
            Log.e("ARDebug", "Failed to initialize AR Renderer", e)
            throw e
        }


        val rootLayout = FrameLayout(this)

        // สร้าง ARSceneView และให้มันจัดการ permission เอง
        arSceneView = ARSceneView(this).apply {
            Log.d("ARDebug", "ARSceneView created")
            // ตั้งค่า callback สำหรับ permission
            arCore.cameraPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                Log.d("ARDebug", "Camera permission result: $granted")
                if (granted) {
                    Toast.makeText(this@ARSceneViewActivity, "Camera permission granted", Toast.LENGTH_SHORT).show()
                    startARSession()
                } else {
                    Toast.makeText(this@ARSceneViewActivity, "Camera permission denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        rootLayout.addView(
            arSceneView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        Log.d("ARDebug", "ARSceneView added to layout")

        // OverlayView
        overlayView = OverlayView(this, arSceneView)
        rootLayout.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        Log.d("ARDebug", "OverlayView added")

        // Back Button
        val backButton = Button(this).apply {
            text = "Back to Home"
            setOnClickListener { finish() }
        }
        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            val marginBottom = (resources.displayMetrics.heightPixels * 0.2f).toInt()
            setMargins(0, 0, 0, marginBottom)
        }
        rootLayout.addView(backButton, buttonParams)

        setContentView(rootLayout)
        Log.d("ARDebug", "Content view set")

        // เริ่ม AR session
        startARSession()
    }

    private fun startARSession() {
        Log.d("ARDebug", "startARSession called")
        if (isARSessionStarted) return
        
        Log.d("ARDebug", "Starting AR Session...")
        try {
            // Configure AR session
            arSceneView.configureSession { session, config ->
                Log.d("ARDebug", "Inside configureSession callback")
                this.session = session
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                try {
                    session.configure(config)
                    Log.d("ARDebug", "AR Session configured successfully")
                } catch (e: Exception) {
                    Log.e("ARDebug", "Session configuration failed", e)
                    throw e
                }
            }

            Log.d("ARDebug", "Setting up onFrame callback...")

            // ตั้งค่า onFrame callback
            arSceneView.onFrame = { _ ->
                session?.let { s ->
                    try {
                        Log.d("ARDebug", "Updating AR frame...")
                        val frame: Frame = s.update()
                        Log.d("ARDebug", "Frame updated successfully: timestamp=${frame.timestamp}")

                        // val now = System.nanoTime()
                        // if (now - lastFrameTime < frameIntervalNs) {
                        //     return@let
                        // }
                        // lastFrameTime = now
                        // Log.d("ARDebug", "Processing frame at 30fps, timestamp=$lastFrameTime")

                        Log.d("ARDebug", "Start converting YUV to tensor")
                        val tensor = onnxHandler.convertYUVToTensor(frame)
                        Log.d("ARDebug", "Tensor conversion completed, length=${tensor.size}")

                        Log.d("ARDebug", "Start running ONNX inference")
                        val output = onnxHandler.runOnnxInference(tensor)
                        Log.d("ARDebug", "ONNX inference completed, detections found: ${output.size}")

                        val detections: List<com.arapp.modules.Detection> = output.map { det ->
                            Log.d(
                                "ARDebug",
                                "Mapping detection: x=${det.x}, y=${det.y}, w=${det.w}, h=${det.h}, conf=${det.confidence}"
                            )
                            com.arapp.modules.Detection(
                                xCenter = det.x,
                                yCenter = det.y,
                                width = det.w,
                                height = det.h,
                                confidence = det.confidence
                            )
                        }

                        Log.d("ARDebug", "Start overlay update, detections count=${detections.size}")
                        runOnUiThread {
                            overlayView.detections = detections
                            overlayView.invalidate()
                            Log.d("ARDebug", "Overlay updated successfully")
                        }

                        val bestDetection = detections.maxByOrNull { it.confidence }
                        Log.d("ARDebug", "Start rendering simple cube node")
                        if (bestDetection != null) {
                            runOnUiThread {
                                arRenderer.renderSimpleCubeNode(arSceneView, bestDetection)
                                Log.d("ARDebug", "Rendered simple cube node for detection: $bestDetection")
                            }
                        } else {
                            Log.d("ARDebug", "No detections to render")
                        }

                        // Log.d("ARDebug", "Start rendering 3D model boxes")
                        // val pos3D = arRenderer.get3DPos(frame, detections)
                        // Log.d("ARDebug", "3D positions calculated, count=${pos3D.size}")
                        // if (pos3D.isNotEmpty()) {
                        //     runOnUiThread {
                        //         arRenderer.renderModelBoxes(arSceneView, pos3D)
                        //         Log.d("ARDebug", "3D model boxes rendered successfully")
                        //     }
                        // } else {
                        //     Log.d("ARDebug", "No 3D positions to render")
                        // }

                    } catch (e: Exception) {
                        Log.e("ARSceneViewActivity", "Error in onFrame", e)
                    }
                }
            }

            isARSessionStarted = true
            
        } catch (e: Exception) {
            Log.e("ARSceneViewActivity", "Failed to configure AR session", e)
            Toast.makeText(this, "Failed to start AR: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::arSceneView.isInitialized && isARSessionStarted) {
            try {
                arSceneView.arCore.resume(this, this)
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onStart", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::arSceneView.isInitialized && isARSessionStarted) {
            try {
                arSceneView.arCore.resume(this, this)
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onResume", e)
            }
        }
    }

    override fun onPause() {
        if (::arSceneView.isInitialized) {
            try {
                arSceneView.arCore.pause()
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onPause", e)
            }
        }
        super.onPause()
    }

    override fun onStop() {
        if (::arSceneView.isInitialized) {
            try {
                arSceneView.arCore.pause()
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onStop", e)
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::arSceneView.isInitialized) {
            try {
                arSceneView.destroy()
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onDestroy", e)
            }
        }
        onnxHandler.close()
        super.onDestroy()
    }
}