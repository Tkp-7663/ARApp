package com.arapp.modules

import com.google.ar.core.Frame
import android.graphics.Color
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.distanceTo // ! data type
import io.github.sceneview.math.*
import io.github.sceneview.node.Node
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Position
import io.github.sceneview.node.PlaneNode
import kotlin.collections.minus
import io.github.sceneview.collision.*

class ARRenderer {

    data class Pose3D(
        val position: Vector3,
        val rotation: Quaternion,
        val scale: Vector3 = Vector3(1f, 1f, 1f)
    )

    private val blueNodes = mutableListOf<Node>()
    private val redNodes = mutableListOf<Node>()

    // Update / reuse blue boxes
    fun updateOnnxBoundingBoxes(sceneView: ARSceneView, detections: FloatArray, modelInputSize: Int = 320) {
        // ซ่อน node เก่า
        blueNodes.forEach { it.isVisible = false }

        // ขนาดหน้าจอจริง
        val screenWidth = sceneView.width.toFloat()
        val screenHeight = sceneView.height.toFloat()
        val scaleX = screenWidth / modelInputSize
        val scaleY = screenHeight / modelInputSize

        val step = 5
        for (i in 0 until detections.size step step) {
            val xCenter = detections[i] * scaleX
            val yCenter = detections[i + 1] * scaleY
            val w = detections[i + 2] * scaleX
            val h = detections[i + 3] * scaleY
            val confidence = detections[i + 4]

            if (confidence < 0.3f) continue // filter low confidence

            val x = xCenter - w / 2f
            val y = yCenter - h / 2f

            // ใช้ node เดิมถ้ามี หรือสร้างใหม่
            val blueBoundingBox = blueNodes.getOrNull(i / step) ?: PlaneNode(
                engine = sceneView.engine,
                size = Float3(w, h, 0f), // ! data type
                materialInstance = sceneView.materialLoader.createColorInstance(
                    Color.argb((0.3f * 255).toInt(), 0, 0, 255)
                )
            ).also {
                sceneView.addChildNode(it)
                blueNodes.add(it)
            }

            // อัปเดตตำแหน่งและขนาดทุก frame
            blueBoundingBox.position = Vector3(x + w / 2f, y + h / 2f, -1f) // Z คงที่เพื่อ overlay // ! data type
            blueBoundingBox(size = Float3(w, h, 0f)) // ! data type
            blueBoundingBox.isVisible = true
        }
    }

    // Update / reuse red boxes
    fun updateModelBoxes(sceneView: ARSceneView, pose3DList: List<Pose3D>, minDistance: Float = 0.2f) {
        // ซ่อน node เก่า
        redNodes.forEach { it.isVisible = false }

        for (pose in pose3DList) {
            // หา node ที่ใกล้ที่สุด // ! distanceTo
            val closestNode = redNodes.minByOrNull { it.worldPosition.distanceTo(pose.position) }

            // ! distanceTo
            if (closestNode != null && closestNode.worldPosition.distanceTo(pose.position) < minDistance) {
                // Reuse node
                closestNode.worldPosition = pose.position // ! data type
                closestNode.worldRotation = pose.rotation // ! data type
                closestNode.worldScale = pose.scale // ! data type
                closestNode.isVisible = true
            } else {
                // สร้าง PlaneNode ใหม่
                val newNode = PlaneNode(
                    engine = sceneView.engine,
                    size = Float(0.45f, 0.45f, 0f), // ! data type
                    materialInstance = sceneView.materialLoader.createColorInstance(
                        Color.argb((0.3f * 255).toInt(), 255, 0, 0)
                    )
                ).apply {
                    worldPosition = pose.position // ! data type
                    worldRotation = pose.rotation // ! data type
                    worldScale = pose.scale // ! data type
                    isVisible = true
                }

                sceneView.addChildNode(newNode)
                redNodes.add(newNode)
            }
        }
    }

    // Get 3D positions with hitTest (แก้ให้ตรง YOLOv11n)
    fun get3DPos(frame: Frame, detections: FloatArray, confidenceThreshold: Float = 0.3f): List<Pose3D> {
        val poses = mutableListOf<Pose3D>()

        val step = 5 // [x_center, y_center, w, h, confidence]
        for (i in 0 until detections.size step step) {
            val xCenter = detections[i]
            val yCenter = detections[i + 1]
            val w = detections[i + 2]
            val h = detections[i + 3]
            val confidence = detections[i + 4]

            if (confidence < confidenceThreshold) continue

            val centerX = xCenter
            val centerY = yCenter

            // ปรับ top และ right ตามสัดส่วน 30%
            val topX = centerX
            val topY = centerY - h * 0.3f
            val rightX = centerX + w * 0.3f
            val rightY = centerY

            val hitsCenter = frame.hitTest(centerX, centerY)
            val hitsTop = frame.hitTest(topX, topY)
            val hitsRight = frame.hitTest(rightX, rightY)

            if (hitsCenter.isEmpty() || hitsTop.isEmpty() || hitsRight.isEmpty()) continue

            val p0 = Vector3(
                hitsCenter[0].hitPose.tx(),
                hitsCenter[0].hitPose.ty(),
                hitsCenter[0].hitPose.tz()
            )
            val p1 = Vector3(
                hitsTop[0].hitPose.tx(),
                hitsTop[0].hitPose.ty(),
                hitsTop[0].hitPose.tz()
            )
            val p2 = Vector3(
                hitsRight[0].hitPose.tx(),
                hitsRight[0].hitPose.ty(),
                hitsRight[0].hitPose.tz()
            )

            val forwardAxis = Vector3.subtract(p1, p0).normalized()
            val rightAxis = Vector3.subtract(p2, p0).normalized()
            val upAxis = Vector3.cross(forwardAxis, rightAxis).normalized()

            val rotation = Quaternion.lookRotation(forwardAxis, upAxis)
            poses.add(Pose3D(position = p0, rotation = rotation))
        }

        return poses
    }

    // Clear nodes
    fun clearNodes(sceneView: ARSceneView) {
        blueNodes.forEach { sceneView.removeChildNode(it) }
        redNodes.forEach { sceneView.removeChildNode(it) }
        blueNodes.clear()
        redNodes.clear()
    }
}
