package com.arapp.modules

import com.google.ar.core.Frame
import android.graphics.Color
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.*
import io.github.sceneview.node.Node
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.node.PlaneNode
import dev.romainguy.kotlin.math.Float3
import kotlin.math.sqrt
import android.util.Log

class ARRenderer {

    data class Pose3D(
        val position: Float3,
        val rotation: Float3,
        val scale: Float3 = Float3(1f, 1f, 1f)
    )

    private val blueNodes = mutableListOf<Node>()
    private val redNodes = mutableListOf<Node>()

    // Update / reuse red boxes
    fun renderModelBoxes(sceneView: ARSceneView, pose3DList: List<Pose3D>, minDistance: Float = 0.25f) {
        Log.d("ARDebug", "Start rendering ${pose3DList.size} 3D poses")

        // ซ่อน node เก่า
        redNodes.forEach { 
            it.isVisible = false 
        }
        Log.d("ARDebug", "All existing redNodes hidden, count=${redNodes.size}")

        for ((index, pose) in pose3DList.withIndex()) {
            Log.d("ARDebug", "Processing pose #$index at position=${pose.position}, rotation=${pose.rotation}")

            // หา node ที่ใกล้ที่สุด
            val closestNode = redNodes.minByOrNull { distance(it.position, pose.position) }
            val dist = closestNode?.let { distance(it.position, pose.position) } ?: Float.MAX_VALUE
            Log.d("ARDebug", "Closest node distance=$dist")

            if (closestNode != null && dist < minDistance) {
                // Reuse node
                Log.d("ARDebug", "Reusing existing node for pose #$index")
                closestNode.position = pose.position
                closestNode.rotation = pose.rotation
                closestNode.scale = pose.scale
                closestNode.isVisible = true
            } else {
                // สร้าง PlaneNode ใหม่
                Log.d("ARDebug", "Creating new PlaneNode for pose #$index")
                val newNode = PlaneNode(
                    engine = sceneView.engine,
                    size = Float3(0.45f, 0.45f, 0f),
                    materialInstance = sceneView.materialLoader.createColorInstance(
                        Color.RED //argb((0.3f * 255).toInt(), 255, 0, 0)
                    )
                ).apply {
                    position = pose.position
                    rotation = pose.rotation
                    scale = pose.scale
                    isVisible = true
                }

                sceneView.addChildNode(newNode)
                redNodes.add(newNode)
                Log.d("ARDebug", "New node added, total redNodes=${redNodes.size}")
            }
        }

        Log.d("ARDebug", "Finished rendering 3D model boxes")
    }

    private fun distance(p1: Float3, p2: Float3): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // Get 3D positions with hitTest (แก้ให้ตรง YOLOv11n)
    fun get3DPos(frame: Frame, detections: List<Detection>, confidenceThreshold: Float = 0.3f): List<Pose3D> {
        val poses = mutableListOf<Pose3D>()
        Log.d("ARDebug", "Start computing 3D poses from ${detections.size} detections")

        detections.forEachIndexed { index, det ->
            Log.d("ARDebug", "Processing detection #$index: x=${det.xCenter}, y=${det.yCenter}, w=${det.width}, h=${det.height}, conf=${det.confidence}")

            if (det.confidence < confidenceThreshold) {
                Log.d("ARDebug", "Detection #$index skipped due to confidence < $confidenceThreshold")
                return@forEachIndexed
            }

            val centerX = det.xCenter
            val centerY = det.yCenter
            val topX = centerX
            val topY = centerY - det.height * 0.1f
            val rightX = centerX + det.width * 0.1f
            val rightY = centerY

            val hitsCenter = frame.hitTest(centerX, centerY)
            val hitsTop = frame.hitTest(topX, topY)
            val hitsRight = frame.hitTest(rightX, rightY)

            Log.d("ARDebug", "Detection #$index hits: center=${hitsCenter.size}, top=${hitsTop.size}, right=${hitsRight.size}")

            if (hitsCenter.isEmpty() || hitsTop.isEmpty() || hitsRight.isEmpty()) {
                Log.d("ARDebug", "Detection #$index skipped due to missing hit test results")
                return@forEachIndexed
            }

            val p0 = hitsCenter[0].hitPose.toVector3()
            val p1 = hitsTop[0].hitPose.toVector3()
            val p2 = hitsRight[0].hitPose.toVector3()

            val forwardAxis = Vector3.subtract(p1, p0).normalized()
            val rightAxis = Vector3.subtract(p2, p0).normalized()
            val upAxis = Vector3.cross(forwardAxis, rightAxis).normalized()

            val rotation = Quaternion.lookRotation(forwardAxis, upAxis)
            val euler = rotation.getEulerAngles()

            Log.d("ARDebug", "Detection #$index pose: position=(${p0.x}, ${p0.y}, ${p0.z}), rotation Euler=(${euler.x}, ${euler.y}, ${euler.z})")

            poses.add(Pose3D(
                position = p0.toFloat3(),
                rotation = euler.toFloat3()
            ))
        }

        Log.d("ARDebug", "Finished computing 3D poses. Total valid poses: ${poses.size}")
        return poses
    }

    private fun com.google.ar.core.Pose.toVector3(): Vector3 {
        return Vector3(tx(), ty(), tz())
    }

    // Clear nodes
    fun clearNodes(sceneView: ARSceneView) {
        blueNodes.forEach { sceneView.removeChildNode(it) }
        redNodes.forEach { sceneView.removeChildNode(it) }
        blueNodes.clear()
        redNodes.clear()
    }

    data class Detection(
        val xCenter: Float,
        val yCenter: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
    )

    private var cubeNode: CubeNode? = null

    fun renderSimpleCubeNode(sceneView: ARSceneView, detection: Detection) {
        // ได้ center จาก onnx (ขนาด input 320x320)
        val onnxX = detection.xCenter
        val onnxY = detection.yCenter

        // normalize -> screen pixel
        val normX = onnxX / 320f
        val normY = onnxY / 320f
        val screenX = normX * sceneView.width
        val screenY = normY * sceneView.height

        val centerX = sceneView.width / 2f
        val centerY = sceneView.height / 2f
        val initialPos = sceneView.screenToWorld(centerX, centerY, z = 1.0f)

        // screen -> world (fix z = 1m)
        val worldPos = sceneView.screenToWorld(screenX, screenY, z = 1.0f)

        Log.d(
            "ARDebug",
            "Render cube from detection: onnx=($onnxX,$onnxY) -> screen=($screenX,$screenY) -> world=$worldPos conf=${detection.confidence}"
        )

        if (cubeNode == null) {
            // สร้างใหม่ครั้งแรก
            cubeNode = CubeNode(
                engine = sceneView.engine,
                size = Float3(0.4f, 0.4f, 0.4f), // 40 cm
                materialInstance = sceneView.materialLoader.createColorInstance(Color.RED)
            ).apply {
                position = initialPos
                rotation = Float3(0f, 0f, 0f)
                scale = Float3(1f, 1f, 1f)
                isVisible = true
            }
            sceneView.addChildNode(cubeNode!!)
            Log.d("ARDebug", "New cube created at world=$worldPos")
        } else {
            // อัปเดตตำแหน่ง cube เดิม
            cubeNode?.apply {
                this.position = worldPos
                this.rotation = Float3(0f, 0f, 0f)
                this.isVisible = true
            }
            Log.d("ARDebug", "Cube updated at world=$worldPos")
        }
    }
}