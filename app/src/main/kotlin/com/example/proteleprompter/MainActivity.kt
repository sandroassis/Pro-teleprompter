package com.example.proteleprompter

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
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
        val repository = DataRepository.getInstance(applicationContext)
        handleIncomingSharedScript(intent, repository)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSharedScript(intent, DataRepository.getInstance(applicationContext))
    }

    private fun handleIncomingSharedScript(intent: Intent?, repository: DataRepository) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                val firstLine = sharedText.lines()
                    .firstOrNull { it.isNotBlank() && !it.startsWith("===") && !it.startsWith("[") }
                    ?.take(42)
                    ?: "Roteiro Importado da IA"
                repository.createNewScript(
                    title = "IA: $firstLine",
                    folder = "IA Studio",
                    initialContent = sharedText
                )
                Toast.makeText(
                    applicationContext,
                    "✨ Roteiro recebido do App de IA e salvo em 'IA Studio'!",
                    Toast.LENGTH_LONG
                ).show()
                intent.action = Intent.ACTION_MAIN
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
