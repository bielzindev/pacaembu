package com.pacaembu.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pacaembu.core.AppRepository
import com.pacaembu.core.Prefs
import com.pacaembu.lock.LockService
import com.pacaembu.ui.iosWindow

class HomeActivity : ComponentActivity() {
    private lateinit var state: HomeState
    private var hadFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iosWindow()
        state = HomeState(this)
        setContent { HomeScreen(state) }
        if (Prefs.lockScreen.value) LockService.start(this)
    }

    override fun onResume() {
        super.onResume()
        iosWindow()
        AppRepository.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hadFocus = hasFocus
        if (hasFocus) iosWindow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the home screen: iOS goes to the first page, then Spotlight.
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (Intent.ACTION_MAIN == intent.action && hadFocus && !broughtToFront) state.homeSeq++
    }
}
