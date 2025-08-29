package com.arapp.modules

import android.content.Intent
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class ARLauncherModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "ARLauncher"

    @ReactMethod
    fun openARActivity() {
        currentActivity?.let {
            val intent = Intent(it, ARActivity::class.java)
            it.startActivity(intent)
        }
    }
}
