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
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.CameraNotAvailableException
import kotlinx.coroutines.*

class ARSceneViewActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var onnxHandler: OnnxRuntimeHandler
    private lateinit var overlayView: OverlayView
    private lateinit var arRenderer: ARRenderer
    private var session: Session? = null
    private var isARSessionStarted = false
    private var isDestroyed = false
    
    // CoroutineScope สำหรับจัดการ async operations
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ตรวจสอบ camera permission ก่อน
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            setupViews()
        } catch (e: Exception) {
            Log.e("ARSceneViewActivity", "Error in onCreate", e)
            Toast.makeText(this, "Failed to initialize AR", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupViews() {
        onnxHandler = OnnxRuntimeHandler(applicationContext)
        arRenderer = ARRenderer()

        val rootLayout = FrameLayout(this)

        // สร้าง ARSceneView พร้อม error handling และปิด hit testing
        arSceneView = ARSceneView(this).apply {
            // ปิด hit testing เพื่อลด "No point hit" errors
            isHitTestingEnabled = false
            
            arCore.cameraPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted && !isDestroyed) {
                    Toast.makeText(this@ARSceneViewActivity, "Camera permission granted", Toast.LENGTH_SHORT).show()
                    startARSession()
                } else if (!isDestroyed) {
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

        // OverlayView
        overlayView = OverlayView(this, arSceneView)
        rootLayout.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Back Button
        val backButton = Button(this).apply {
            text = "Back to Home"
            setOnClickListener { 
                if (!isDestroyed) finish() 
            }
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

        // เริ่ม AR session แบบ async
        startARSessionSafely()
    }

    private fun startARSessionSafely() {
        if (isDestroyed) return
        
        activityScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Delay เล็กน้อยเพื่อให้ UI setup เสร็จ
                    delay(100)
                }
                if (!isDestroyed) {
                    startARSession()
                }
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error starting AR session", e)
                if (!isDestroyed) {
                    Toast.makeText(this@ARSceneViewActivity, "Failed to start AR", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun startARSession() {
        if (isARSessionStarted || isDestroyed) return

        try {
            // Configure AR session พร้อมการป้องกัน MediaPipe errors
            arSceneView.configureSession { session, config ->
                this.session = session
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                
                // เพิ่มการตั้งค่าเพื่อลด performance issues และ stabilize camera
                config.focusMode = Config.FocusMode.AUTO
                
                // ลองตั้งค่าให้ conservative กว่าเดิม
                try {
                    session.configure(config)
                    Log.d("ARSceneViewActivity", "AR Session configured successfully")
                } catch (e: Exception) {
                    Log.e("ARSceneViewActivity", "Session configuration failed", e)
                    // ลองใช้ config ที่ basic กว่า
                    config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    session.configure(config)
                }
            }

            // ตั้งค่า onFrame callback แบบ safe พร้อม error recovery
            arSceneView.onFrame = onFrame@{ _ ->
                if (isDestroyed || !activityScope.isActive) return@onFrame
                
                try {
                    processARFrame()
                } catch (e: Exception) {
                    Log.v("ARSceneViewActivity", "OnFrame error: ${e.message}")
                    // ไม่ให้ crash แอป
                }
            }

            isARSessionStarted = true
            Log.d("ARSceneViewActivity", "AR Session started successfully")
            
        } catch (e: CameraNotAvailableException) {
            Log.e("ARSceneViewActivity", "Camera not available", e)
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e("ARSceneViewActivity", "Failed to configure AR session", e)
            Toast.makeText(this, "Failed to start AR: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ตัวแปรสำหรับจำกัด frame processing rate
    private var lastProcessTime = 0L
    private var frameSkipCount = 0
    
    private fun processARFrame() {
        if (isDestroyed) return
        
        // จำกัด frame processing rate (ประมาณ 10 FPS)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < 100) {
            frameSkipCount++
            return
        }
        lastProcessTime = currentTime
        
        session?.let { s ->
            try {
                val frame: Frame = s.update()
                
                // ตรวจสอบ frame state ก่อนประมวลผล
                if (frame.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) {
                    Log.v("ARSceneViewActivity", "Camera not tracking, skip frame")
                    return@let
                }
                
                // ทำ inference ใน background thread แต่จำกัดจำนวน concurrent jobs
                if (activityScope.isActive) {
                    activityScope.launch(Dispatchers.IO) launch@{
                        if (isDestroyed) return@launch
                        
                        try {
                            val tensor = try {
                                onnxHandler.convertYUVToTensor(frame)
                            } catch (e: NotYetAvailableException) {
                                Log.v("ARSceneViewActivity", "Camera image not available, skip frame")
                                return@launch
                            } catch (e: IllegalStateException) {
                                Log.v("ARSceneViewActivity", "Camera state error, skip frame")
                                return@launch
                            }
                            
                            val output = onnxHandler.runOnnxInference(tensor)
                            val detections = output.map { det ->
                                Detection(
                                    xCenter = det.x,
                                    yCenter = det.y,
                                    width = det.w,
                                    height = det.h,
                                    confidence = det.confidence
                                )
                            }

                            // Update UI ใน main thread (แต่ตรวจสอบ state อีกครั้ง)
                            withContext(Dispatchers.Main) {
                                if (!isDestroyed && ::overlayView.isInitialized && 
                                    !this@ARSceneViewActivity.isFinishing) {
                                    try {
                                        overlayView.detections = detections
                                        overlayView.invalidate()
                                    } catch (e: Exception) {
                                        Log.v("ARSceneViewActivity", "UI update error: ${e.message}")
                                    }
                                }
                            }

                        } catch (e: OutOfMemoryError) {
                            Log.w("ARSceneViewActivity", "Memory error during inference, will retry")
                            System.gc() // Force garbage collection
                        } catch (e: Exception) {
                            Log.v("ARSceneViewActivity", "ONNX inference error: ${e.message}")
                        }
                    }
                }

            } catch (e: CameraNotAvailableException) {
                Log.v("ARSceneViewActivity", "Camera temporarily unavailable")
            } catch (e: IllegalStateException) {
                Log.v("ARSceneViewActivity", "AR Session state error")
            } catch (e: Exception) {
                Log.v("ARSceneViewActivity", "Frame processing error: ${e.message}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isDestroyed && ::arSceneView.isInitialized && isARSessionStarted) {
            try {
                arSceneView.arCore.resume(this, this)
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onStart", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isDestroyed && ::arSceneView.isInitialized && isARSessionStarted) {
            try {
                // เพิ่ม delay เล็กน้อยเพื่อให้ system พร้อม
                activityScope.launch {
                    delay(50)
                    if (!isDestroyed && !isFinishing) {
                        withContext(Dispatchers.Main) {
                            try {
                                arSceneView.arCore.resume(this@ARSceneViewActivity, this@ARSceneViewActivity)
                            } catch (e: Exception) {
                                Log.e("ARSceneViewActivity", "Error in resume", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onResume", e)
            }
        }
    }

    override fun onPause() {
        if (::arSceneView.isInitialized && !isDestroyed) {
            try {
                arSceneView.arCore.pause()
                // เคลียร์ frame processing ที่ค้างอยู่
                lastProcessTime = 0L
                frameSkipCount = 0
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onPause", e)
            }
        }
        super.onPause()
    }

    override fun onStop() {
        if (::arSceneView.isInitialized && !isDestroyed) {
            try {
                arSceneView.arCore.pause()
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error in onStop", e)
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        isDestroyed = true
        
        // ปิด frame processing ก่อน
        lastProcessTime = 0L
        
        // Cancel ทุก coroutines และรอให้เสร็จ
        try {
            runBlocking {
                activityScope.cancel()
                // รอให้ job ที่กำลังทำงานเสร็จสิ้น
                activityScope.coroutineContext[Job]?.join()
            }
        } catch (e: Exception) {
            Log.v("ARSceneViewActivity", "Error canceling coroutines: ${e.message}")
        }
        
        // ปิด AR resources อย่างระมัดระวัง
        if (::arSceneView.isInitialized) {
            try {
                // พยายาม pause ก่อน destroy
                arSceneView.arCore.pause()
                Thread.sleep(100) // ให้เวลา cleanup
                arSceneView.destroy()
            } catch (e: Exception) {
                Log.e("ARSceneViewActivity", "Error destroying ARSceneView", e)
            }
        }
        
        // ปิด ONNX handler
        try {
            if (::onnxHandler.isInitialized) {
                onnxHandler.close()
            }
        } catch (e: Exception) {
            Log.e("ARSceneViewActivity", "Error closing ONNX handler", e)
        }
        
        session = null
        
        // Force garbage collection เพื่อเคลียร์ memory
        System.gc()
        
        super.onDestroy()
    }
}