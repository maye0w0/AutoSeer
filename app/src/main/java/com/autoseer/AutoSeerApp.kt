package com.autoseer

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/** Application entry point. Initializes the bundled OpenCV native library once. */
class AutoSeerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val ok = OpenCVLoader.initLocal()
        Log.i(TAG, if (ok) "OpenCV 初始化成功" else "OpenCV 初始化失敗")
    }

    companion object {
        const val TAG = "AutoSeer"
    }
}
