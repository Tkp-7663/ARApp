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
        // ซ่อน node เก่า
        redNodes.forEach { it.isVisible = false }

        for (pose in pose3DList) {
            // หา node ที่ใกล้ที่สุด
            val closestNode = redNodes.minByOrNull { distance(it.position, pose.position) }

            if (closestNode != null && distance(closestNode.position, pose.position) < minDistance) {
                // Reuse node
                closestNode.position = pose.position
                closestNode.rotation = pose.rotation
                closestNode.scale = pose.scale
                closestNode.isVisible = true
            } else {
                // สร้าง PlaneNode ใหม่
                val newNode = PlaneNode(
                    engine = sceneView.engine,
                    size = Float3(0.45f, 0.45f, 0f),
                    materialInstance = sceneView.materialLoader.createColorInstance(
                        Color.argb((0.3f * 255).toInt(), 255, 0, 0)
                    )
                ).apply {
                    position = pose.position
                    rotation = pose.rotation
                    scale = pose.scale
                    isVisible = true
                }

                sceneView.addChildNode(newNode)
                redNodes.add(newNode)
            }
        }
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

        detections.forEach { det ->
            if (det.confidence < confidenceThreshold) return@forEach

            val centerX = det.xCenter
            val centerY = det.yCenter

            // ปรับ top และ right ตามสัดส่วน 10%
            val topX = centerX
            val topY = centerY - det.height * 0.1f
            val rightX = centerX + det.width * 0.1f
            val rightY = centerY

            val hitsCenter = frame.hitTest(centerX, centerY)
            val hitsTop = frame.hitTest(topX, topY)
            val hitsRight = frame.hitTest(rightX, rightY)

            if (hitsCenter.isEmpty() || hitsTop.isEmpty() || hitsRight.isEmpty()) return@forEach

            val p0 = hitsCenter[0].hitPose.toVector3()
            val p1 = hitsTop[0].hitPose.toVector3()
            val p2 = hitsRight[0].hitPose.toVector3()

            // คำนวณแกน
            val forwardAxis = Vector3.subtract(p1, p0).normalized()
            val rightAxis = Vector3.subtract(p2, p0).normalized()
            val upAxis = Vector3.cross(forwardAxis, rightAxis).normalized()

            // สร้าง Quaternion จากแกน
            val rotation = Quaternion.lookRotation(forwardAxis, upAxis)

            // ใช้ Quaternion โดยตรง (ถ้า Pose3D ต้องการ Euler ให้แปลง)
            poses.add(Pose3D(
                position = p0.toFloat3(),
                rotation = rotation.getEulerAngles().toFloat3()
            ))
        }

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
}
