package com.arapp.modules

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode
import dev.romainguy.kotlin.math.Float3
import com.google.ar.core.*
import java.io.File

class MarkerRenderer(private val context: Context) {

    companion object {
        const val MARKER_DB = "markers/ar_marker_database.imgdb"
        const val MODEL_PATH = "models/wheel1.obj"
    }

    // session จะถูกกำหนดจาก ARSceneView
    lateinit var session: Session
    private var wheelNode: ModelNode? = null
    var isModelLoaded = false
        private set

    fun setupDatabase() {
        try {
            Log.d("MarkerRenderer", "Setting up marker database...")
            val dbFile = File(context.filesDir, MARKER_DB)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                context.assets.open(MARKER_DB).use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("MarkerRenderer", "Marker database copied to filesDir")
            }

            val imgDb = AugmentedImageDatabase.deserialize(session, dbFile.inputStream())
            val config = session.config.apply {
                augmentedImageDatabase = imgDb
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            session.configure(config)
            Log.d("MarkerRenderer", "AR Session configured with marker database")
        } catch (e: Exception) {
            Log.e("MarkerRenderer", "Failed to setup marker database", e)
        }
    }

    fun loadModel(sceneView: ARSceneView) {
        if (isModelLoaded) {
            Log.d("MarkerRenderer", "Model already loaded")
            return
        }
        try {
            Log.d("MarkerRenderer", "Loading model from $MODEL_PATH")
            wheelNode = ModelNode(
                modelInstance = sceneView.modelLoader.createModelInstance(assetFileLocation = MODEL_PATH)
            ).apply {
                scale = Float3(0.5f, 0.5f, 0.5f)
                isVisible = false
            }
            sceneView.addChildNode(wheelNode!!)
            isModelLoaded = true
            Log.d("MarkerRenderer", "ModelNode added to scene")
        } catch (e: Exception) {
            Log.e("MarkerRenderer", "Error loading model", e)
        }
    }

    fun updateNodePosition(augmentedImage: AugmentedImage) {
        if (!isModelLoaded) return
        val pose = augmentedImage.centerPose
        val position = Float3(pose.tx(), pose.ty(), pose.tz())
        wheelNode?.apply {
            this.position = position
            this.rotation = Float3(0f, 0f, 0f)
            this.isVisible = augmentedImage.trackingState == TrackingState.TRACKING
        }
        Log.d("MarkerRenderer", "Wheel model position updated: $position, tracking=${augmentedImage.trackingState}")
    }

    fun cleanup() {
        wheelNode?.destroy()
        wheelNode = null
        isModelLoaded = false
        if (::session.isInitialized) session.close()
        Log.d("MarkerRenderer", "Resources cleaned up")
    }
}
