package com.example.proteleprompter.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.proteleprompter.data.DataRepository
import com.example.proteleprompter.ui.theme.ProTeleprompterTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        val repo = DataRepository(storageFile = null)
        val vm = MainScreenViewModel(repo)
        composeTestRule.setContent {
            ProTeleprompterTheme {
                MainScreen(
                    onItemClick = {},
                    viewModel = vm
                )
            }
        }
    }

    @Test
    fun studioHeader_andDefaultScripts_exist() {
        composeTestRule.onNodeWithText("PRO TELEPROMPTER").assertExists()
        composeTestRule.onNodeWithText("Telejornal / Plantão de Notícias").assertExists()
    }
}
