package com.arapp.utils

import com.google.ar.core.Pose
import kotlin.math.*

class CoordinateMapper {

    /**
     * Calculate 6DoF pose with real-world offset in centimeters
     */
    fun calculatePoseWithOffset(hitPose: Pose, offsetCm: Float): Pose {
        // Convert cm to meters
        val offsetM = offsetCm / 100.0f
        
        // Get the forward direction from the hit pose
        val forwardX = -hitPose.zAxis[0] * offsetM
        val forwardY = -hitPose.zAxis[1] * offsetM
        val forwardZ = -hitPose.zAxis[2] * offsetM
        
        // Apply offset to position
        val newTranslation = floatArrayOf(
            hitPose.tx() + forwardX,
            hitPose.ty() + forwardY,
            hitPose.tz() + forwardZ
        )
        
        // Keep the same rotation
        val rotation = floatArrayOf(
            hitPose.qx(),
            hitPose.qy(),
            hitPose.qz(),
            hitPose.qw()
        )
        
        return Pose(newTranslation, rotation)
    }

    /**
     * Convert screen coordinates to normalized coordinates
     */
    fun screenToNormalized(screenX: Float, screenY: Float, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val normalizedX = (screenX / screenWidth.toFloat()) * 2.0f - 1.0f
        val normalizedY = -((screenY / screenHeight.toFloat()) * 2.0f - 1.0f)
        return Pair(normalizedX, normalizedY)
    }

    /**
     * Calculate distance between two 3D points
     */
    fun calculateDistance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Convert quaternion to Euler angles (degrees)
     */
    fun quaternionToEuler(qx: Float, qy: Float, qz: Float, qw: Float): Triple<Float, Float, Float> {
        // Roll (x-axis rotation)
        val sinr_cosp = 2 * (qw * qx + qy * qz)
        val cosr_cosp = 1 - 2 * (qx * qx + qy * qy)
        val roll = atan2(sinr_cosp, cosr_cosp) * 180.0f / PI.toFloat()

        // Pitch (y-axis rotation)
        val sinp = 2 * (qw * qy - qz * qx)
        val pitch = if (abs(sinp) >= 1) {
            (PI.toFloat() / 2).withSign(sinp) * 180.0f / PI.toFloat()
        } else {
            asin(sinp) * 180.0f / PI.toFloat()
        }

        // Yaw (z-axis rotation)
        val siny_cosp = 2 * (qw * qz + qx * qy)
        val cosy_cosp = 1 - 2 * (qy * qy + qz * qz)
        val yaw = atan2(siny_cosp, cosy_cosp) * 180.0f / PI.toFloat()

        return Triple(roll, pitch, yaw)
    }
}
