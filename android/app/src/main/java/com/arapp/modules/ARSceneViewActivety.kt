package com.arapp.modules

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.activity.ComponentActivity
import io.github.sceneview.ar.ARSceneView
import com.arapp.utils.OnnxRuntimeHandler
import com.google.ar.core.Frame
import com.google.ar.core.Session


class ARSceneViewActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var arRenderer: ARRenderer
    private lateinit var onnxHandler: OnnxRuntimeHandler
    private lateinit var overlayView: OverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onnxHandler = OnnxRuntimeHandler(applicationContext)
        arRenderer = ARRenderer()

        // Root layout
        val rootLayout = FrameLayout(this)

        val session = Session(this)

        // ARSceneView
        arSceneView = ARSceneView(this).apply {
            onFrame = { frameTimeNanos: Long ->
                val frame: Frame = session.update()
                val tensor = onnxHandler.convertYUVToTensor(frame)
                val output = onnxHandler.runOnnxInference(tensor)

                // Overlay blue rectangles every frame
                overlayView.detections = output
                overlayView.invalidate()

                // Render red model boxes (only once)
                val pos3D = arRenderer.get3DPos(frame, output)
                if (pos3D.isNotEmpty()) {
                    arRenderer.renderModelBoxes(this, pos3D)
                }
            }
        }

        // Add ARSceneView to root
        rootLayout.addView(
            arSceneView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Add OverlayView to root
        overlayView = OverlayView(this, arSceneView)
        rootLayout.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Overlay Button
        val backButton = Button(this).apply {
            text = "Back to Home"
            setOnClickListener { finish() } // กลับไปหน้า Home
        }

        // Position button 20% จากขอบล่าง
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
    }

    override fun onStart() {
        super.onStart()
        arSceneView.arCore.resume(this, this)
    }

    override fun onStop() {
        arSceneView.arCore.pause()
        super.onStop()
    }

    override fun onDestroy() {
        // Clear all nodes
        // arRenderer.clearNodes(arSceneView)
        arSceneView.destroy()
        super.onDestroy()
    }
}
