package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Stable LSPosed settings entry, independent from the optional desktop launcher. */
class ModuleSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }
}
