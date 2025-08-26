package com.arapp.modules

import android.view.View
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.ArSceneView
import io.github.sceneview.node.ArModelNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "SceneViewModule"

    private var arSceneView: ArSceneView? = null
    private val arNodes = mutableListOf<ArModelNode>()

    @ReactMethod
    fun initializeScene(viewTag: Int, promise: Promise) {
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)
        
        uiManager?.addUIBlock { nativeViewHierarchyManager ->
            try {
                val view = nativeViewHierarchyManager.resolveView(viewTag) as? View
                if (view != null) {
                    // Create AR SceneView
                    arSceneView = ArSceneView(reactApplicationContext).apply {
                        // Configure SceneView settings
                        planeRenderer.isEnabled = true
                        planeRenderer.isShadowReceiver = true
                        
                        // Set up lighting
                        lightEstimationMode = io.github.sceneview.ar.ArSceneView.LightEstimationMode.ENVIRONMENTAL_HDR
                    }
                    
                    promise.resolve(null)
                } else {
                    promise.reject("ERROR", "Could not find view with tag: $viewTag")
                }
            } catch (e: Exception) {
                promise.reject("ERROR", "Scene initialization failed: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun renderBlueBox(pose6DoF: ReadableMap, promise: Promise) {
        if (arSceneView == null) {
            promise.reject("ERROR", "SceneView not initialized")
            return
        }

        try {
            val position = pose6DoF.getArray("position")
            val rotation = pose6DoF.getArray("rotation")
            
            if (position == null || rotation == null) {
                promise.reject("ERROR", "Invalid pose data")
                return
            }

            GlobalScope.launch {
                try {
                    // Create blue cube node
                    val cubeNode = CubeNode().apply {
                        // Set size (10cm cube)
                        scale = Position(0.1f, 0.1f, 0.1f)
                        
                        // Set blue material color
                        setMaterialColor(
                            android.graphics.Color.BLUE,
                            android.graphics.Color.BLUE,
                            android.graphics.Color.BLUE,
                            android.graphics.Color.BLUE
                        )
                        
                        // Set position from 6DoF pose
                        worldPosition = Position(
                            position.getDouble(0).toFloat(),
                            position.getDouble(1).toFloat(),
                            position.getDouble(2).toFloat()
                        )
                        
                        // Set rotation from quaternion
                        worldRotation = Rotation(
                            rotation.getDouble(0).toFloat(), // qx
                            rotation.getDouble(1).toFloat(), // qy
                            rotation.getDouble(2).toFloat(), // qz
                            rotation.getDouble(3).toFloat()  // qw
                        )
                    }

                    // Add to scene
                    arSceneView?.addChild(cubeNode)
                    
                    // Store reference for cleanup
                    if (cubeNode is ArModelNode) {
                        arNodes.add(cubeNode)
                    }
                    
                    promise.resolve(null)
                    
                } catch (e: Exception) {
                    promise.reject("ERROR", "Failed to render blue box: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            promise.reject("ERROR", "Blue box rendering failed: ${e.message}")
        }
    }

    @ReactMethod
    fun clearAllBoxes(promise: Promise) {
        try {
            arNodes.forEach { node ->
                arSceneView?.removeChild(node)
            }
            arNodes.clear()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to clear boxes: ${e.message}")
        }
    }

    @ReactMethod
    fun cleanup(promise: Promise? = null) {
        try {
            clearAllBoxes(null)
            arSceneView?.destroy()
            arSceneView = null
            promise?.resolve(null)
        } catch (e: Exception) {
            promise?.reject("ERROR", "Cleanup failed: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        cleanup()
    }
}
