package com.arapp.modules

import android.content.Intent
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class ARLauncherModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ARLauncher"

    @ReactMethod
    fun openARActivity(promise: Promise) {
        val activity = currentActivity
        if (activity != null) {
            try {
                val intent = Intent(activity, ARSceneViewActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("AR_OPEN_FAILED", e)
            }
        } else {
            promise.reject("NO_CURRENT_ACTIVITY", "Current activity is null")
        }
    }
}
