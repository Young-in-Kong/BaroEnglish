package com.example.baroenglish

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.settingsBackButton).setOnClickListener {
            finish()
        }

        val playbackModeGroup = findViewById<RadioGroup>(R.id.playbackModeGroup)
        playbackModeGroup.check(
            when (PlaybackSettings.getPlaybackMode(this)) {
                PlaybackMode.REPEAT_ONE -> R.id.repeatOneButton
                PlaybackMode.REPEAT_ALL -> R.id.repeatAllButton
                PlaybackMode.PLAY_ONCE -> R.id.playOnceButton
            }
        )
        playbackModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.repeatOneButton -> PlaybackMode.REPEAT_ONE
                R.id.repeatAllButton -> PlaybackMode.REPEAT_ALL
                else -> PlaybackMode.PLAY_ONCE
            }
            PlaybackSettings.setPlaybackMode(this, mode)
        }
    }
}
