package com.arapp.modules

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.sceneview.ar.ARSceneView
import com.arapp.utils.OnnxRuntimeHandler

class ARActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OnnxRuntimeHandler instance
        val onnxHandler = OnnxRuntimeHandler(applicationContext)
        // ARSceneViewModule instance
        val sceneView = ARSceneViewModule(this)

        // create ARSceneView
        arSceneView = ARSceneView(this).apply {
            // Callback capture frame
            onFrame = { frame ->
                frame?.let { f ->
                    // Convert frame เป็น tensor
                    val tensor = onnxHandler.convertYUVToTensor(f)
                    // Run inference
                    val output = onnxHandler.runOnnxInference(tensor)

                    // Render blue boxes จาก Onnx
                    sceneView.renderOnnxBoxs(this, output)

                    // Render red boxes 3D model with position + rotation
                    val pos6dof = sceneView.getPos6dof(output)
                    sceneView.renderModelBoxes(this, pos6dof)
                }
            }
        }

        setContentView(arSceneView)
    }

    override fun onStart() {
        super.onStart()
        arSceneView.resume()
    }

    override fun onStop() {
        arSceneView.pause()
        super.onStop()
    }

    override fun onDestroy() {
        arSceneView.destroy()
        super.onDestroy()
    }
}
