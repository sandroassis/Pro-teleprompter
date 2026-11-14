package com.example.proteleprompter

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class Editor(val scriptId: String) : NavKey

@Serializable
data class Prompter(val scriptId: String) : NavKey

@Serializable
data class GeminiAi(val scriptId: String? = null) : NavKey

@Serializable
data object CloudBackup : NavKey

@Serializable
data object RemoteControl : NavKey

@Serializable
data object StudioSettings : NavKey

@Serializable
data object About : NavKey
