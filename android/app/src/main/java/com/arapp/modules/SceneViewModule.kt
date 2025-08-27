package com.arapp.modules

import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.module.annotations.ReactModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Color
import io.github.sceneview.node.CubeNode
import io.github.sceneview.material.setBaseColorFactor
import android.view.ViewGroup
import android.view.View
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Activity
import android.util.Log
import com.google.ar.core.Session

@ReactModule(name = "SceneViewModule")
class SceneViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "SceneViewModule"

    private var arSceneView: ARSceneView? = null
    private val arNodes = mutableListOf<CubeNode>()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isSceneReady = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val TAG = "SceneViewModule"
    }

    private fun requestCameraPermission(promise: Promise) {
        val currentActivity: Activity = currentActivity ?: run {
            promise.reject("ERROR", "Current activity is null")
            return
        }

        if (ContextCompat.checkSelfPermission(
                currentActivity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Camera permission already granted")
            promise.resolve(null)
        } else {
            Log.d(TAG, "Requesting camera permission")
            ActivityCompat.requestPermissions(
                currentActivity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
            promise.reject("PERMISSION_REQUIRED", "Camera permission is required for AR functionality")
        }
    }

    private fun createARSceneView(): ARSceneView? {
        return try {
            val currentActivity = currentActivity ?: return null
            
            ARSceneView(currentActivity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // เปิดใช้งาน plane detection
                planeRenderer.isEnabled = true
                planeRenderer.isShadowReceiver = true

                // กำหนดค่า light estimation
                lightEstimator?.environmentalHdrMainLightDirection = true

                // เปิดใช้งาน ARSceneView
                isEnabled = true
                
                Log.d(TAG, "ARSceneView created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARSceneView", e)
            null
        }
    }

    @ReactMethod
    fun initializeScene(viewTag: Int, promise: Promise) {
        Log.d(TAG, "initializeScene called with viewTag: $viewTag")
        
        val uiManager = reactApplicationContext.getNativeModule(UIManagerModule::class.java)
        uiManager?.addUIBlock { nativeViewHierarchyManager ->
            scope.launch {
                try {
                    val view = nativeViewHierarchyManager.resolveView(viewTag) as? ViewGroup
                    if (view == null) {
                        Log.e(TAG, "Could not find view with tag: $viewTag")
                        promise.reject("ERROR", "Could not find view with tag: $viewTag")
                        return@launch
                    }

                    Log.d(TAG, "View found, checking camera permission")
                    
                    // ตรวจสอบ permission
                    if (ContextCompat.checkSelfPermission(
                            reactApplicationContext,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // สร้าง ARSceneView
                        arSceneView = createARSceneView()
                        
                        if (arSceneView != null) {
                            // เพิ่ม ARSceneView ลงใน container
                            view.removeAllViews() // ลบ view เก่าออกก่อน
                            view.addView(arSceneView)
                            Log.d(TAG, "ARSceneView added to container")
                            promise.resolve(null)
                        } else {
                            promise.reject("ERROR", "Failed to create ARSceneView")
                        }
                    } else {
                        Log.d(TAG, "Camera permission not granted")
                        requestCameraPermission(promise)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Scene initialization failed", e)
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

                Log.d(TAG, "Starting AR session")
                
                // รอให้ ARSession พร้อม
                var retryCount = 0
                while (arSceneView?.session == null && retryCount < 20) {
                    delay(250)
                    retryCount++
                    Log.d(TAG, "Waiting for AR session... retry: $retryCount")
                }

                if (arSceneView?.session != null) {
                    isSceneReady = true
                    Log.d(TAG, "AR session started successfully")
                    promise.resolve(null)
                } else {
                    promise.reject("ERROR", "AR session failed to start")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AR session", e)
                promise.reject("ERROR", "Failed to start AR session: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun renderBlueBox(pose6DoF: ReadableMap, promise: Promise) {
        if (arSceneView == null) {
            promise.reject("ERROR", "SceneView not initialized")
            return
        }

        if (!isSceneReady) {
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
                }

                arSceneView?.addChildNode(cubeNode)
                arNodes.add(cubeNode)
                
                Log.d(TAG, "Blue box rendered at position: ${cubeNode.worldPosition}")
                promise.resolve(null)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to render blue box", e)
                promise.reject("ERROR", "Failed to render blue box: ${e.message}")
            }
        }
    }

    @ReactMethod
    fun clearAllBoxes(promise: Promise) {
        try {
            arNodes.forEach { node -> 
                arSceneView?.removeChildNode(node)
            }
            arNodes.clear()
            Log.d(TAG, "All boxes cleared")
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear boxes", e)
            promise.reject("ERROR", "Failed to clear boxes: ${e.message}")
        }
    }

    @ReactMethod
    fun pauseScene(promise: Promise?) {
        try {
            // ไม่มี pause() ให้เรียกแล้ว → เราจำลองว่าถูก pause
            isSceneReady = false
            arSceneView?.onSessionPaused = { session ->
                Log.d(TAG, "AR Session paused via callback")
            }
            Log.d(TAG, "Scene paused (flag only)")
            promise?.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause scene", e)
            promise?.reject("ERROR", "Failed to pause scene: ${e.message}")
        }
    }

    @ReactMethod
    fun resumeScene(promise: Promise?) {
        try {
            // ไม่มี resume() ให้เรียก → จำลองว่า resumed
            isSceneReady = true
            arSceneView?.onSessionResumed = { session ->
                Log.d(TAG, "AR Session resumed via callback")
            }
            Log.d(TAG, "Scene resumed (flag only)")
            promise?.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume scene", e)
            promise?.reject("ERROR", "Failed to resume scene: ${e.message}")
        }
    }

    @ReactMethod
    fun cleanup(promise: Promise?) {
        try {
            isSceneReady = false
            arNodes.forEach { node -> arSceneView?.removeChildNode(node) }
            arNodes.clear()
            arSceneView?.destroy()
            arSceneView = null
            Log.d(TAG, "Cleanup completed")
            promise?.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            promise?.reject("ERROR", "Cleanup failed: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.d(TAG, "Catalyst instance destroying")
        cleanup(null)
    }
}