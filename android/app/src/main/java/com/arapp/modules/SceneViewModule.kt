package com.arapp.modules

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.module.annotations.ReactModule
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Color
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.material.setBaseColorFactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.arapp.utils.ImagesConverter
import com.google.ar.core.TrackingState
import android.media.Image

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "SceneViewModule"

    private var arSceneView: ARSceneView? = null
    private val arNodes = mutableListOf<CubeNode>()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isSceneReady = false
    private val imageConverter = ImagesConverter()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val TAG = "SceneViewModule"
    }

    private fun requestCameraPermission(promise: Promise) {
        val currentActivity: Activity = currentActivity ?: run {
            promise.reject("ERROR", "Current activity is null")
            return
        }

        if (ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            promise.resolve(null)
        } else {
            ActivityCompat.requestPermissions(currentActivity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            promise.reject("PERMISSION_REQUIRED", "Camera permission is required")
        }
    }

    private fun createARSceneView(): ARSceneView? {
        val activity = currentActivity ?: return null
        return ARSceneView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            planeRenderer.isEnabled = true
            planeRenderer.isShadowReceiver = true
            isEnabled = true
        }
    }

    @ReactMethod
    fun initializeScene(viewTag: Int, promise: Promise) {
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)
        uiManager?.addUIBlock { nativeViewHierarchyManager ->
            scope.launch {
                try {
                    val view = nativeViewHierarchyManager.resolveView(viewTag) as? ViewGroup
                        ?: run {
                            promise.reject("ERROR", "Could not find view with tag: $viewTag")
                            return@launch
                        }

                    if (ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                        arSceneView = createARSceneView()
                        arSceneView?.let { arView ->
                            view.removeAllViews()
                            view.addView(arView)
                            promise.resolve(null)
                        } ?: promise.reject("ERROR", "Failed to create ARSceneView")
                    } else {
                        requestCameraPermission(promise)
                    }

                } catch (e: Exception) {
                    promise.reject("ERROR", "Scene initialization failed: ${e.message}")
                }
            }
        }
    }

    @ReactMethod
    fun startARSession(promise: Promise) {
        scope.launch {
            try {
                if (arSceneView == null) {
                    promise.reject("ERROR", "ARSceneView not initialized")
                    return@launch
                }

                var retryCount = 0
                while (arSceneView?.session == null && retryCount < 20) {
                    delay(250)
                    retryCount++
                }

                if (arSceneView?.session != null) {
                    isSceneReady = true
                    promise.resolve(null)
                } else {
                    promise.reject("ERROR", "AR session failed to start")
                }
            } catch (e: Exception) {
                promise.reject("ERROR", "Failed to start AR session: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun captureFrame(promise: Promise) {
        if (!isSceneReady || arSceneView?.session == null) {
            promise.reject("ERROR", "AR session not ready")
            return
        }

        try {
            val frame = arSceneView!!.session?.update() ?: run {
                promise.resolve(null)
                return
            }

            val camera = frame.camera
            if (camera.trackingState == TrackingState.TRACKING) {
                val image: Image = frame.acquireCameraImage()
                try {
                    val tensorData = imageConverter.convertYuvToTensor(image)
                    promise.resolve(tensorData)
                } finally {
                    image.close()
                }
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Frame capture failed: ${e.message}")
        }
    }

    @ReactMethod
    fun renderBlueBox(pose6DoF: ReadableMap, promise: Promise) {
        if (!isSceneReady || arSceneView == null) {
            promise.reject("ERROR", "AR session not ready")
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
                val cubeNode = CubeNode(engine = arSceneView!!.engine).apply {
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
    fun pauseScene(promise: Promise?) {
        isSceneReady = false
        promise?.resolve(null)
    }

    @ReactMethod
    fun resumeScene(promise: Promise?) {
        isSceneReady = true
        promise?.resolve(null)
    }

    @ReactMethod
    fun cleanup(promise: Promise?) {
        try {
            isSceneReady = false
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
        cleanup(null)
    }
}
