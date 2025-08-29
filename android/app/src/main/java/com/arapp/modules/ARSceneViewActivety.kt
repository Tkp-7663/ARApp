package com.example.arapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.sceneview.ar.ARSceneView

class ARActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // สร้าง ARSceneView
        arSceneView = ARSceneView(this).apply {

            // Callback ดักจับ frame
            onFrame = { frame ->
                // แปลง frame เป็น Bitmap
                val bitmap = frame.toBitmap()  // ฟังก์ชันแปลง frame -> Bitmap

                // ส่ง bitmap ให้ ONNX inference
                OnnxHandler.runInference(bitmap)
            }
        }

        setContentView(arSceneView)
    }

    override fun onResume() {
        super.onResume()
        arSceneView.resume()  // auto start AR session
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()   // pause AR session
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy() // cleanup
    }
}
