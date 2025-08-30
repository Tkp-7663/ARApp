package com.arapp.modules

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import android.graphics.Color
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.colorOf
import io.github.sceneview.math.toVector3
import io.github.sceneview.node.PlaneNode

class ARRenderer {

    data class Pose3D(
        val position: Vector3,
        val rotation: Quaternion,
        val scale: Vector3 = Vector3(1f, 1f, 1f)
    )

    // Render the onnx bounding boxes on screen (blue)
    fun renderOnnxBoundingBoxes(sceneView: ARSceneView, detections: FloatArray) {
        for (i in detections.indices step 6) {
            val x = detections[i]
            val y = detections[i + 1]
            val w = detections[i + 2]
            val h = detections[i + 3]

            // Convert 2D pixel position to 3D world position using screenToWorld
            val worldPos = sceneView.screenToWorld(
                x + w / 2f,       // center x
                y + h / 2f,       // center y
                1f                // distance from camera
            )

			// create material instance - cheer blue 30%
			val blueMaterial = arSceneView.materialLoader.createColorInstance(
				Color.argb((0.3f * 255).toInt(), 0, 0, 255)
			)

            val boxNode = PlaneNode(
                engine = arSceneView.engine,
				size = Float3(w, h, 0f),
				materialInstance = blueMaterial
            ).apply {
				position = Position(x, y, -1f)
			}
            sceneView.addChildNode(boxNode)
        }
    }

    // Render the model boxes on the AR scene (red)
    fun renderModelBoxes(sceneView: ARSceneView, pose3DList: List<Pose3D>) {
		// create material instance - cheer red 30%
		val redMaterial = arSceneView.materialLoader.createColorInstance(
			Color.argb((0.3f * 255).toInt(), 255, 0, 0)
		)

        for (pose in pose3DList) {
            val modelNode = PlaneNode(
				engine = sceneView.engine,
				size = Float3(0.45f, 0.45f, 0f),
				materialInstance = redMaterial
            ).apply {
				position = pose.position
				rotation = pose.rotation
				scale = pose.scale
			}
            sceneView.addChildNode(modelNode)
        }
    }

    // Get 3D position with hitTest (3 points
    fun get3DPos(frame: Frame, detections: FloatArray): List<Pose3D> {
        val poses = mutableListOf<Pose3D>()

        for (i in detections.indices step 6) {
            val x = detections[i]
            val y = detections[i + 1]
            val w = detections[i + 2]
            val h = detections[i + 3]

            val centerX = x + w / 2f
            val centerY = y + h / 2f
            val topX = centerX
            val topY = y
            val rightX = x + w
            val rightY = centerY

            val hitsCenter = frame.hitTest(centerX, centerY)
            val hitsTop = frame.hitTest(topX, topY)
            val hitsRight = frame.hitTest(rightX, rightY)

            if (hitsCenter.isEmpty() || hitsTop.isEmpty() || hitsRight.isEmpty()) continue

            // Convert Pose -> Float3 -> Vector3
            val p0 = Float3(hitsCenter[0].hitPose.tx(), hitsCenter[0].hitPose.ty(), hitsCenter[0].hitPose.tz()).toVector3()
            val p1 = Float3(hitsTop[0].hitPose.tx(), hitsTop[0].hitPose.ty(), hitsTop[0].hitPose.tz()).toVector3()
            val p2 = Float3(hitsRight[0].hitPose.tx(), hitsRight[0].hitPose.ty(), hitsRight[0].hitPose.tz()).toVector3()

            // Calculate axes
            val forwardAxis = Vector3.normalized(Vector3.subtract(p1, p0))
            val rightAxis = Vector3.normalized(Vector3.subtract(p2, p0))
            val upAxis = Vector3.normalized(Vector3.cross(forwardAxis, rightAxis))

            val rotation = quaternionFromAxes(forwardAxis, upAxis, rightAxis)
            poses.add(Pose3D(position = p0, rotation = rotation))
        }

        return poses
    }

    // Create Quaternion from 3 axes
    private fun quaternionFromAxes(forward: Vector3, up: Vector3, right: Vector3): Quaternion {
        val m00 = right.x;   val m01 = right.y;   val m02 = right.z
        val m10 = up.x;      val m11 = up.y;      val m12 = up.z
        val m20 = forward.x; val m21 = forward.y; val m22 = forward.z

        val trace = m00 + m11 + m22
        return if (trace > 0) {
            val s = 0.5f / kotlin.math.sqrt((trace + 1.0).toDouble()).toFloat()
            Quaternion(
                (m21 - m12) * s,
                (m02 - m20) * s,
                (m10 - m01) * s,
                0.25f / s
            )
        } else {
            // fallback: return identity quaternion if trace <= 0
            Quaternion.identity()
        }
    }
}
