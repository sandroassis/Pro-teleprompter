package com.example.proteleprompter

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.proteleprompter.data.DataRepository
import com.example.proteleprompter.hardware.RemoteInputBus
import com.example.proteleprompter.ui.theme.ProTeleprompterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataRepository.getInstance(applicationContext)

        enableEdgeToEdge()
        setContent {
            ProTeleprompterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (RemoteInputBus.handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
