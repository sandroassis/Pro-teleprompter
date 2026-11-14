package com.example.proteleprompter.hardware

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class RemoteCommand(val label: String) {
    TOGGLE_PLAY_PAUSE("Play / Pause"),
    SPEED_UP("Velocidade +"),
    SPEED_DOWN("Velocidade -"),
    NEXT_MARKER("Próximo Tópico"),
    PREV_MARKER("Tópico Anterior"),
    RESTART("Reiniciar do Topo"),
    TOGGLE_MIRROR("Espelhar Texto")
}

object RemoteInputBus {
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<RemoteCommand> = _commands.asSharedFlow()

    var isPrompterActive: Boolean = false
    var interceptVolumeKeys: Boolean = true

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isPrompterActive) return false

        val command = when (keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> RemoteCommand.TOGGLE_PLAY_PAUSE

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_EQUALS -> RemoteCommand.SPEED_UP

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MINUS -> RemoteCommand.SPEED_DOWN

            KeyEvent.KEYCODE_VOLUME_UP -> if (interceptVolumeKeys) RemoteCommand.SPEED_UP else null
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (interceptVolumeKeys) RemoteCommand.SPEED_DOWN else null

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteCommand.NEXT_MARKER

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteCommand.PREV_MARKER

            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_MOVE_HOME -> RemoteCommand.RESTART

            KeyEvent.KEYCODE_M -> RemoteCommand.TOGGLE_MIRROR
            else -> null
        }

        return if (command != null) {
            _commands.tryEmit(command)
            true
        } else {
            false
        }
    }
}

