package com.example.proteleprompter.ui.main

import com.example.proteleprompter.data.DataRepository
import com.example.proteleprompter.data.ScriptParser
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun repository_loadsDefaultScripts_andCalculatesDuration() = runTest {
        val repo = DataRepository(storageFile = null)
        val viewModel = MainScreenViewModel(repo)
        val state = viewModel.uiState.first()
        assertTrue(state.scripts.isNotEmpty())
        assertTrue(state.scripts.first().wordCount > 0)
    }

    @Test
    fun scriptParser_extractsPausesAndMarkersCorrectly() {
        val sample = """
            === ABERTURA ===
            Olá mundo [DESTAQUE]importante[/DESTAQUE]
            [PAUSA 3s]
            === FECHAMENTO ===
            Até logo [PAUSA 2s]
        """.trimIndent()

        assertEquals(5, ScriptParser.calculateTotalPauseSeconds(sample))
        assertEquals(2, ScriptParser.extractMarkers(sample).size)
        assertEquals("ABERTURA", ScriptParser.extractMarkers(sample)[0].title)
        assertEquals(5, ScriptParser.countSpokenWords(sample))
    }

    @Test
    fun geminiAssistant_generatesStructuredBroadcastScriptInNaturalLanguage() = runTest {
        val repo = DataRepository(storageFile = null)
        val viewModel = MainScreenViewModel(repo)
        viewModel.geminiAssistant.sendUserMessage("Crie um roteiro curto de 30s para Reels sobre finanças pessoais")
        val messages = viewModel.geminiAssistant.messages.value
        val lastMessage = messages.last()
        assertEquals(false, lastMessage.isUser)
        assertTrue(!lastMessage.extractedScript.isNullOrBlank())
        assertTrue(lastMessage.extractedScript!!.contains("[PAUSA"))
    }
}
