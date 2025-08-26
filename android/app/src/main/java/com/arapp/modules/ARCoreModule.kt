package com.arapp.modules

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.facebook.react.bridge.*
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.arapp.utils.CoordinateMapper
import com.arapp.utils.ImagesConverter
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = "ARCoreModule")
class ARCoreModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var ortSession: OrtSession

    override fun getName() = "ARCoreModule"

    private var arSession: Session? = null
    private var isSessionStarted = false
    private val coordinateMapper = CoordinateMapper()
    private val imageConverter = ImagesConverter()

    @ReactMethod
    fun initializeAR(promise: Promise) {
        try {
            val activity = currentActivity ?: run {
                promise.reject("ERROR", "Activity not available")
                return
            }

            // Check AR availability
            val availability = ArCoreApk.getInstance().checkAvailability(activity)
            if (availability.isTransient) {
                // Continue to check availability
            }

            when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    // ARCore is installed and supported
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    promise.reject("ERROR", "ARCore needs to be updated or installed")
                    return
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    promise.reject("ERROR", "Device not compatible with ARCore")
                    return
                }
                else -> {
                    promise.reject("ERROR", "Unknown ARCore availability")
                    return
                }
            }

            // Create AR session
            arSession = Session(activity).apply {
                val config = Config(this).apply {
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }
                configure(config)
            }

            promise.resolve(true)

        } catch (e: UnavailableArcoreNotInstalledException) {
            promise.reject("ERROR", "ARCore not installed: ${e.message}")
        } catch (e: UnavailableApkTooOldException) {
            promise.reject("ERROR", "ARCore APK too old: ${e.message}")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            promise.reject("ERROR", "Device not compatible: ${e.message}")
        } catch (e: Exception) {
            promise.reject("ERROR", "AR initialization failed: ${e.message}")
        }
    }

    @ReactMethod
    fun startARSession(promise: Promise) {
        try {
            arSession?.let { session ->
                session.resume()
                isSessionStarted = true
                promise.resolve(null)
            } ?: run {
                promise.reject("ERROR", "AR session not initialized")
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to start AR session: ${e.message}")
        }
    }

    @ReactMethod
    fun stopARSession(promise: Promise) {
        try {
            arSession?.let { session ->
                session.pause()
                isSessionStarted = false
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to stop AR session: ${e.message}")
        }
    }

    @ReactMethod
    fun captureFrame(promise: Promise) {
        if (!isSessionStarted || arSession == null) {
            promise.reject("ERROR", "AR session not active")
            return
        }

        try {
            arSession?.let { session ->
                val frame = session.update()
                val camera = frame.camera
                
                if (camera.trackingState == TrackingState.TRACKING) {
                    val cameraImage = frame.acquireCameraImage()
                    
                    // Convert YUV to tensor format
                    val tensorData = imageConverter.convertYuvToTensor(cameraImage)
                    cameraImage.close()
                    
                    // Return tensor data as base64 string
                    promise.resolve(tensorData)
                } else {
                    promise.resolve(null)
                }
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Frame capture failed: ${e.message}")
        }
    }

    @ReactMethod
    fun hitTestWithOffset(x: Float, y: Float, offsetCm: Float, promise: Promise) {
        if (!isSessionStarted || arSession == null) {
            promise.reject("ERROR", "AR session not active")
            return
        }

        try {
            arSession?.let { session ->
                val frame = session.update()
                
                // Perform hit test at center point
                val hits = frame.hitTest(x, y)
                
                if (hits.isNotEmpty()) {
                    val hit = hits.first()
                    val anchor = hit.createAnchor()
                    
                    // Calculate 6DoF pose with offset
                    val pose6DoF = coordinateMapper.calculatePoseWithOffset(
                        hit.hitPose, 
                        offsetCm
                    )
                    
                    val result = Arguments.createMap().apply {
                        putArray("position", Arguments.createArray().apply {
                            pushDouble(pose6DoF.tx().toDouble())
                            pushDouble(pose6DoF.ty().toDouble())
                            pushDouble(pose6DoF.tz().toDouble())
                        })
                        putArray("rotation", Arguments.createArray().apply {
                            pushDouble(pose6DoF.qx().toDouble())
                            pushDouble(pose6DoF.qy().toDouble())
                            pushDouble(pose6DoF.qz().toDouble())
                            pushDouble(pose6DoF.qw().toDouble())
                        })
                    }
                    
                    promise.resolve(result)
                } else {
                    promise.resolve(null)
                }
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Hit test failed: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        ortSession?.close()
        ortEnvironment?.close()
    }
}
