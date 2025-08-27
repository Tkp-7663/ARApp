package com.arapp.modules

import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.module.annotations.ReactModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.toQuaternion
import io.github.sceneview.math.Color
import io.github.sceneview.node.CubeNode
import io.github.sceneview.material.setBaseColorFactor
import android.view.ViewGroup
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Activity

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "SceneViewModule"

    private var arSceneView: ARSceneView? = null
    private val arNodes = mutableListOf<CubeNode>()
    private val scope = CoroutineScope(Dispatchers.Main)

    private fun requestCameraPermission(onGranted: () -> Unit) {
        val currentActivity: Activity = currentActivity ?: return

        if (ContextCompat.checkSelfPermission(
                currentActivity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(
                currentActivity,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    @ReactMethod
    fun initializeScene(viewTag: Int, promise: Promise) {
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)
        uiManager?.addUIBlock { nativeViewHierarchyManager ->
            try {
                val view = nativeViewHierarchyManager.resolveView(viewTag) as? ViewGroup
                if (view == null) {
                    promise.reject("ERROR", "Could not find view with tag: $viewTag")
                    return@addUIBlock
                }

                requestCameraPermission {
                    // สร้าง ARSceneView
                    arSceneView = ARSceneView(reactApplicationContext).apply {
                        setLayoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        planeRenderer.isEnabled = true
                        planeRenderer.isShadowReceiver = true
                        lightEstimator?.environmentalHdrMainLightDirection = true
                        isEnabled = true
                        onVisibilityAggregated = true
                        }
                    // arSceneView?.layoutParams = ViewGroup.LayoutParams(
                    //     ViewGroup.LayoutParams.MATCH_PARENT,
                    //     ViewGroup.LayoutParams.MATCH_PARENT
                    // )
                    // เพิ่ม ARSceneView ลง container
                    view.addView(arSceneView)

                    promise.resolve(null)
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

        val positionArray = pose6DoF.getArray("position")
        val rotationArray = pose6DoF.getArray("rotation")
        if (positionArray == null || rotationArray == null) {
            promise.reject("ERROR", "Invalid pose data")
            return
        }

        scope.launch {
            try {
                val cubeNode = CubeNode(
                        engine = arSceneView!!.engine,
                    ).apply {
                    scale = Position(0.1f, 0.1f, 0.1f)
                    materialInstance?.setBaseColorFactor(Color(0.0f, 0.0f, 1.0f, 1.0f))
                    worldPosition = Position(
                        positionArray.getDouble(0).toFloat(),
                        positionArray.getDouble(1).toFloat(),
                        positionArray.getDouble(2).toFloat()
                    )
                    worldRotation = Rotation(
                        rotationArray.getDouble(0).toFloat(),
                        rotationArray.getDouble(1).toFloat(),
                        rotationArray.getDouble(2).toFloat()
                    )
                    // val rotationEuler = Rotation(
                    //     rotationArray.getDouble(0).toFloat(),
                    //     rotationArray.getDouble(1).toFloat(),
                    //     rotationArray.getDouble(2).toFloat()
                    // )
                    // worldRotation = rotationEuler.toQuaternion()
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
