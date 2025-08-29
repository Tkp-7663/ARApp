package com.arapp.modules

import android.content.Context

class ARRenderer(private val context: Context) {

	// Render the onnx bounding boxes on the AR scene
	fun renderOnnxBoxes(position: List) {
	}

	// Render the model boxes on the AR scene
	fun renderModelBoxes(detections: List) {
	}

	// get 6DoF position with hitTest(center + offset){sceneview HitResult}
	fun getPos6DoF(detections: List) {
	}
}
