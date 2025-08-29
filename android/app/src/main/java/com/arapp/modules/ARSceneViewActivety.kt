package com.arapp.modules

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.sceneview.ar.ARSceneView
import com.arapp.modules.OnnxRuntimeHandler

class ARActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OnnxRuntimeHandler instance
        val onnxHandler = OnnxRuntimeHandler(this)
        // ARSceneViewModule instance
        val sceneView = ARSceneViewModule(this)

        // create ARSceneView
        arSceneView = ARSceneView(this).apply {
            // Callback capture frame
            onFrame = { frame ->
                // send frame to OnnxHandler
                val tensor = onnxHandler.convertYUVToTensor(frame)
                val output = onnxHandler.runOnnxInference(tensor)

                // render the onnx blue boxes
                sceneView.renderOnnxBoxs(arSceneView, output)

                // render the model red boxes
                val pos6dof = sceneView.getPos6dof(output)
                sceneView.renderModelBoxes(arSceneView, pos6dof)
            }
        }

        setContentView(arSceneView)
    }

    override fun onResume() {
        super.onResume()
        arSceneView.resume()
    }

    override fun onPause() {
        super.onPause()
        arSceneView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }
}
