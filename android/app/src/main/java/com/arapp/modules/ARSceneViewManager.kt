package com.arapp.modules

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import io.github.sceneview.ar.ARSceneView

class ARSceneViewManager : SimpleViewManager<ARSceneView>() {

    override fun getName(): String = "ARSceneView"

    override fun createViewInstance(reactContext: ThemedReactContext): ARSceneView {
        return ARSceneView(reactContext).apply {
            planeRenderer.isEnabled = true
            planeRenderer.isShadowReceiver = true
            isEnabled = true
        }
    }
}
