package com.arapp.modules

import android.view.View
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "SceneViewModule"

    private var arSceneView: ARSceneView? = null
    private val arNodes = mutableListOf<CubeNode>()
    private val scope = CoroutineScope(Dispatchers.Main)

    @ReactMethod
    fun initializeScene(viewTag: Int, promise: Promise) {
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)
        uiManager?.addUIBlock { nativeViewHierarchyManager ->
            try {
                val view = nativeViewHierarchyManager.resolveView(viewTag) as? View
                if (view == null) {
                    promise.reject("ERROR", "Could not find view with tag: $viewTag")
                    return@addUIBlock
                }

                // สร้าง ArSceneView และเพิ่มลงใน view ที่มีอยู่
                arSceneView = ARSceneView(reactApplicationContext).apply {
                    planeRenderer.isEnabled = true
                    planeRenderer.isShadowReceiver = true
                    lightEstimator.environmentalHdrMainLightDirection = true
                }
                // view เป็น container ของ arSceneView
                if (view is androidx.constraintlayout.widget.ConstraintLayout) {
                    view.addView(arSceneView)
                }

                promise.resolve(null)
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

        val positionArray = pose6DoF.getArray("position")
        val rotationArray = pose6DoF.getArray("rotation")
        if (positionArray == null || rotationArray == null) {
            promise.reject("ERROR", "Invalid pose data")
            return
        }

        scope.launch {
            try {
                val cubeNode = CubeNode().apply {
                    scale = Position(0.1f, 0.1f, 0.1f)
                    setMaterialColor(
                        android.graphics.Color.BLUE,
                        android.graphics.Color.BLUE,
                        android.graphics.Color.BLUE,
                        android.graphics.Color.BLUE
                    )
                    worldPosition = Position(
                        positionArray.getDouble(0).toFloat(),
                        positionArray.getDouble(1).toFloat(),
                        positionArray.getDouble(2).toFloat()
                    )
                    worldRotation = Rotation(
                        rotationArray.getDouble(0).toFloat(),
                        rotationArray.getDouble(1).toFloat(),
                        rotationArray.getDouble(2).toFloat(),
                        rotationArray.getDouble(3).toFloat()
                    )
                }

                arSceneView?.addChildNode(cubeNode)
                arNodes.add(cubeNode)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ERROR", "Failed to render blue box: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun clearAllBoxes(promise: Promise) {
        try {
            arNodes.forEach { node -> arSceneView?.removeChildNode(node) }
            arNodes.clear()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to clear boxes: ${e.message}")
        }
    }

    @ReactMethod
    fun cleanup(promise: Promise? = null) {
        try {
            arNodes.forEach { node -> arSceneView?.removeChildNode(node) }
            arNodes.clear()
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
