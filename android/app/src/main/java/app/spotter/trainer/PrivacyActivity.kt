package app.spotter.trainer

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Explains how Spotter uses health data; Health Connect links here from its permission screens. */
class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            text = "${getString(R.string.privacy_title)}\n\n${getString(R.string.privacy_body)}"
            textSize = 16f
            setPadding(pad, pad * 2, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
