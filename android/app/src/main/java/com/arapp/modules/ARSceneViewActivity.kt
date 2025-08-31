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

        setupViews()
    }

    private fun setupViews() {
        onnxHandler = OnnxRuntimeHandler(applicationContext)
        arRenderer = ARRenderer()

        val rootLayout = FrameLayout(this)

        // สร้าง ARSceneView และให้มันจัดการ permission เอง
        arSceneView = ARSceneView(this).apply {
            // ตั้งค่า callback สำหรับ permission
            arCore.cameraPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted ->
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

        // เริ่ม AR session
        startARSession()
    }

    private fun startARSession() {
        if (isARSessionStarted) return

        try {
            // Configure AR session
            arSceneView.configureSession { session, config ->
                this.session = session
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                session.configure(config)
            }

            // ตั้งค่า onFrame callback
            arSceneView.onFrame = { _ ->
                session?.let { s ->
                    try {
                        val frame: Frame = s.update()
                        val tensor = onnxHandler.convertYUVToTensor(frame)
                        val output = onnxHandler.runOnnxInference(tensor)

                        val detections: List<com.arapp.modules.Detection> = output.map { det ->
                            com.arapp.modules.Detection(
                                xCenter = det.x,
                                yCenter = det.y,
                                width = det.w,
                                height = det.h,
                                confidence = det.confidence
                            )
                        }

                        // Update overlay on UI thread
                        runOnUiThread {
                            overlayView.detections = detections
                            overlayView.invalidate()
                        }

                        // Render 3D model boxes
                        val pos3D = arRenderer.get3DPos(frame, detections)
                        if (pos3D.isNotEmpty()) {
                            runOnUiThread {
                                arRenderer.renderModelBoxes(arSceneView, pos3D)
                            }
                        }
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
