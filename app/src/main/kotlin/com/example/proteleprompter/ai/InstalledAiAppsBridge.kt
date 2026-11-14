package com.example.proteleprompter.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

enum class ExternalAiProvider(
    val displayName: String,
    val packageName: String,
    val playStoreUrl: String,
    val colorHex: Long
) {
    GOOGLE_GEMINI(
        displayName = "Google Gemini",
        packageName = "com.google.android.apps.bard",
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.google.android.apps.bard",
        colorHex = 0xFF38BDF8
    ),
    OPENAI_CHATGPT(
        displayName = "ChatGPT",
        packageName = "com.openai.chatgpt",
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.openai.chatgpt",
        colorHex = 0xFF10B981
    ),
    ANTHROPIC_CLAUDE(
        displayName = "Claude AI",
        packageName = "com.anthropic.claude",
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.anthropic.claude",
        colorHex = 0xFFFBBF24
    ),
    MICROSOFT_COPILOT(
        displayName = "Microsoft Copilot",
        packageName = "com.microsoft.copilot",
        playStoreUrl = "https://play.google.com/store/apps/details?id=com.microsoft.copilot",
        colorHex = 0xFFA78BFA
    )
}

object InstalledAiAppsBridge {

    fun detectInstalledAiApps(context: Context): List<ExternalAiProvider> {
        val pm = context.packageManager
        return ExternalAiProvider.entries.filter { provider ->
            isPackageInstalled(pm, provider.packageName)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildProfessionalTeleprompterPrompt(
        userRequest: String,
        currentTitle: String? = null,
        currentScript: String? = null
    ): String {
        val contextBlock = if (!currentScript.isNullOrBlank()) {
            "\nROTEIRO ATUAL (${currentTitle ?: "Sem título"}):\n$currentScript\n"
        } else {
            ""
        }

        return """
            Atue como um Roteirista e Diretor Profissional de TV/YouTube para Teleprompter.
            Crie ou aprimore o roteiro solicitado abaixo em Português do Brasil, pronto para ser lido no Teleprompter.

            Use obrigatoriamente estas marcações de estúdio no texto:
            - === NOME DO BLOCO === para dividir as seções do roteiro
            - [DICA]orientação de expressão, voz ou enquadramento[/DICA]
            - [DESTAQUE]palavras ou frases com ênfase positiva[/DESTAQUE]
            - [ALERTA]frases de forte impacto ou atenção máxima[/ALERTA]
            - [PAUSA 2s] ou [PAUSA 3s] entre ideias importantes para respiração.
            $contextBlock
            PEDIDO DO USUÁRIO:
            $userRequest

            Responda entregando o roteiro completo e formatado.
        """.trimIndent()
    }

    fun sendPromptToInstalledAiApp(
        context: Context,
        provider: ExternalAiProvider,
        userRequest: String,
        currentTitle: String? = null,
        currentScript: String? = null
    ): Boolean {
        val formattedPrompt = buildProfessionalTeleprompterPrompt(
            userRequest = userRequest.ifBlank { "Crie um roteiro profissional dinâmico de 1 minuto." },
            currentTitle = currentTitle,
            currentScript = currentScript
        )

        // Always copy the formatted studio prompt to clipboard so user can also paste with 1 tap
        copyToClipboard(context, "Prompt Teleprompter", formattedPrompt)

        // 1. Try direct ACTION_SEND to the specific AI app package
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(provider.packageName)
                putExtra(Intent.EXTRA_TEXT, formattedPrompt)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sendIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(sendIntent)
                return true
            }
        } catch (_: Exception) {
        }

        // 2. If Google Gemini is selected, also try ACTION_VOICE_COMMAND / ACTION_ASSIST or launch intent
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(provider.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {
        }

        return false
    }

    fun openPlayStoreToInstall(context: Context, provider: ExternalAiProvider = ExternalAiProvider.GOOGLE_GEMINI) {
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${provider.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(provider.playStoreUrl)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        } catch (_: Exception) {
        }
    }

    fun readClipboardText(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            item?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
