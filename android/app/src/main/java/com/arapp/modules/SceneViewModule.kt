package com.arapp.modules

import android.media.Image
import com.facebook.react.bridge.*
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Color
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.material.setBaseColorFactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.facebook.react.module.annotations.ReactModule
import com.arapp.utils.ImagesConverter
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.NativeViewHierarchyManager
import com.facebook.react.uimanager.UIBlock

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "SceneViewModule"

    private val scope = CoroutineScope(Dispatchers.Main)
    private var arSceneView: ARSceneView? = null
    private val arNodes = mutableListOf<CubeNode>()
    private var isSceneReady = false

    private val imageConverter = ImagesConverter

    fun setARSceneView(view: ARSceneView) {
        arSceneView = view
    }

    @ReactMethod
    fun initializeScene(reactTag: Int, promise: Promise) {
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)

        if (uiManager == null) {
            promise.reject("ERROR", "UIManagerModule not found")
            return
        }

        uiManager.addUIBlock(object : UIBlock {
            override fun execute(nativeViewHierarchyManager: NativeViewHierarchyManager) {
                val view = nativeViewHierarchyManager.resolveView(reactTag)
                if (view is ARSceneView) {
                    setARSceneView(view)
                    promise.resolve(true)
                } else {
                    promise.reject("ERROR", "View is not ARSceneView")
                }
            }
        })
    }

    @ReactMethod
    fun startARSession(promise: Promise) {
        val view = arSceneView
        if (view == null) {
            promise.reject("ERROR", "ARSceneView not initialized")
            return
        }
        isSceneReady = true
        promise.resolve(null)
    }

    @ReactMethod
    fun captureFrame(promise: Promise) {
        val view = arSceneView
        if (!isSceneReady || view?.session == null) {
            promise.reject("ERROR", "AR session not ready")
            return
        }

        try {
            val frame = view.session?.update() ?: run {
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
        val view = arSceneView
        if (!isSceneReady || view == null) {
            promise.reject("ERROR", "AR session not ready")
            return
        }

        val posArray = pose6DoF.getArray("position") ?: run {
            promise.reject("ERROR", "Invalid position")
            return
        }
        val rotArray = pose6DoF.getArray("rotation") ?: run {
            promise.reject("ERROR", "Invalid rotation")
            return
        }

        scope.launch {
            try {
                val cubeNode = CubeNode(engine = view.engine).apply {
                    scale = Position(0.1f, 0.1f, 0.1f)
                    materialInstance?.setBaseColorFactor(Color(0.0f, 0.0f, 1.0f, 1.0f))
                    worldPosition = Position(
                        posArray.getDouble(0).toFloat(),
                        posArray.getDouble(1).toFloat(),
                        posArray.getDouble(2).toFloat()
                    )
                    worldRotation = Rotation(
                        rotArray.getDouble(0).toFloat(),
                        rotArray.getDouble(1).toFloat(),
                        rotArray.getDouble(2).toFloat()
                    )
                }
                view.addChildNode(cubeNode)
                arNodes.add(cubeNode)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ERROR", "Failed to render blue box: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun clearAllBoxes(promise: Promise) {
        arSceneView?.let { view ->
            arNodes.forEach { node -> view.removeChildNode(node) }
            arNodes.clear()
        }
        promise.resolve(null)
    }
}
