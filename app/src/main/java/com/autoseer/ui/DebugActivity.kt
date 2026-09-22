package com.autoseer.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autoseer.R

/**
 * Debug: explains the on-screen debug tools. The actual actions (存畫面／測試偵測)
 * are triggered from the floating control overlay while the service runs.
 */
class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        supportActionBar?.title = getString(R.string.debug_title)
    }
}
