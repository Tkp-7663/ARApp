package com.arapp.modules

import com.google.ar.core.Frame
import android.graphics.Color
import android.view.View
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.*
import io.github.sceneview.utils.screenToWorld
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.node.CubeNode
import dev.romainguy.kotlin.math.Float3
import kotlin.math.sqrt
import android.util.Log
import com.google.ar.core.AugmentedImage
import com.google.ar.core.TrackingState
import io.github.sceneview.node.ModelNode

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

    fun renderSimpleCubeNode(sceneView: ARSceneView, detection: com.arapp.modules.Detection) {
        // แปลง onnx 320x320 -> normalized screen coordinates
        val normalizedX = (detection.xCenter / 320f) * 2f - 1f  // -1 to 1
        val normalizedY = (detection.yCenter / 320f) * 2f - 1f  // -1 to 1
        
        // Simple 3D position calculation - place cube 1 meter in front of camera
        val distance = 1.0f
        val worldPos = Float3(
            normalizedX * distance * 0.5f,  // Scale based on field of view
            -normalizedY * distance * 0.5f, // Flip Y coordinate
            -distance  // In front of camera (negative Z in camera space)
        )

        // Center position for initial cube placement
        // val initialPos = Float3(0f, 0f, -1.0f)

        Log.d(
            "ARDebug",
            "Render cube from detection: screen=($normalizedX,$normalizedY) -> world=$worldPos conf=${detection.confidence}"
        )

        if (cubeNode == null) {
            // สร้างใหม่ครั้งแรก
            cubeNode = CubeNode(
                engine = sceneView.engine,
                size = Float3(0.4f, 0.4f, 0.4f), // 40 cm
                materialInstance = sceneView.materialLoader.createColorInstance(Color.RED)
            ).apply {
                position = worldPos
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

    // private var wheelNode: ModelNode? = null
    // private var isModelLoaded = false

    // fun renderWithMarker(sceneView: ARSceneView, frame: Frame) {
    //     // เอา trackables ที่เป็น AugmentedImage (marker ที่ ARCore track ได้)
    //     val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

    //     for (augmentedImage in updatedAugmentedImages) {
    //         when (augmentedImage.trackingState) {
    //             TrackingState.TRACKING -> {
    //                 // ถ้า marker ถูก detect แล้ว
    //                 val centerPose = augmentedImage.centerPose
    //                 val position = Float3(
    //                     centerPose.tx(),
    //                     centerPose.ty(),
    //                     centerPose.tz()
    //                 )

    //                 // แปลงจาก Pose rotation เป็น Euler angles
    //                 val rotation = poseToEulerAngles(centerPose)

    //                 if (wheelNode == null && !isModelLoaded) {
    //                     isModelLoaded = true
                        
    //                     // สร้าง ModelNode ใหม่ตาม API ของ SceneView
    //                     wheelNode = ModelNode().apply {
    //                         // โหลดโมเดลแบบ async
    //                         loadModelAsync(
    //                             context = sceneView.context,
    //                             lifecycle = sceneView.lifecycle,
    //                             modelFileLocation = "models/wheel.obj", // เก็บใน android/app/src/main/assets/models/
    //                             autoAnimate = false,
    //                             scaleToUnits = 1.0f, // ขนาดหน่วยเป็นเมตร
    //                             centerOrigin = Position(0.0f, 0.0f, 0.0f)
    //                         ) { modelInstance, exception ->
    //                             if (exception != null) {
    //                                 Log.e("ARDebug", "Failed to load wheel model", exception)
    //                                 isModelLoaded = false
    //                             } else {
    //                                 Log.d("ARDebug", "Wheel model loaded successfully")
    //                                 // ตั้งค่าเริ่มต้นหลังโหลดเสร็จ
    //                                 this.position = position
    //                                 this.rotation = rotation
    //                                 this.scale = Float3(0.2f, 0.2f, 0.2f)
    //                                 this.isVisible = true
    //                             }
    //                         }
    //                     }
                        
    //                     sceneView.addChildNode(wheelNode!!)
    //                     Log.d("ARDebug", "Created wheel model node at marker pos=$position")
                        
    //                 } else if (wheelNode != null && isModelLoaded) {
    //                     // อัปเดตตำแหน่งและการหมุนของโมเดลตาม marker
    //                     wheelNode?.apply {
    //                         this.position = position
    //                         this.rotation = rotation
    //                         this.isVisible = true
    //                     }
    //                     Log.d("ARDebug", "Updated wheel model at marker pos=$position, rotation=$rotation")
    //                 }
    //             }

    //             TrackingState.PAUSED -> {
    //                 // marker ถูก track แต่อาจจะไม่ค่อยชัด → แสดงโมเดลแต่อาจจะโปร่งใส
    //                 wheelNode?.apply {
    //                     isVisible = true
    //                     // อาจจะลดความโปร่งใสลง
    //                 }
    //                 Log.d("ARDebug", "Marker tracking paused, keeping wheel visible")
    //             }

    //             TrackingState.STOPPED -> {
    //                 // marker หายไป → ซ่อนโมเดล
    //                 wheelNode?.isVisible = false
    //                 Log.d("ARDebug", "Marker lost, wheel hidden")
    //             }
    //         }
    //     }
    // }

    // // ฟังก์ชันแปลง Pose เป็น Euler angles สำหรับการหมุน
    // private fun poseToEulerAngles(pose: Pose): Float3 {
    //     val quaternion = pose.rotationQuaternion
        
    //     // แปลง Quaternion เป็น Euler angles (X, Y, Z rotation in degrees)
    //     val sinr_cosp = 2 * (quaternion[3] * quaternion[0] + quaternion[1] * quaternion[2])
    //     val cosr_cosp = 1 - 2 * (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1])
    //     val roll = Math.atan2(sinr_cosp.toDouble(), cosr_cosp.toDouble())

    //     val sinp = 2 * (quaternion[3] * quaternion[1] - quaternion[2] * quaternion[0])
    //     val pitch = if (Math.abs(sinp) >= 1) {
    //         Math.copySign(Math.PI / 2, sinp.toDouble())
    //     } else {
    //         Math.asin(sinp.toDouble())
    //     }

    //     val siny_cosp = 2 * (quaternion[3] * quaternion[2] + quaternion[0] * quaternion[1])
    //     val cosy_cosp = 1 - 2 * (quaternion[1] * quaternion[1] + quaternion[2] * quaternion[2])
    //     val yaw = Math.atan2(siny_cosp.toDouble(), cosy_cosp.toDouble())

    //     return Float3(
    //         Math.toDegrees(roll).toFloat(),
    //         Math.toDegrees(pitch).toFloat(),
    //         Math.toDegrees(yaw).toFloat()
    //     )
    // }

    // // ฟังก์ชันสำหรับล้าง resources เมื่อไม่ต้องการใช้แล้ว
    // fun cleanup() {
    //     wheelNode?.destroy()
    //     wheelNode = null
    //     isModelLoaded = false
    // }
}