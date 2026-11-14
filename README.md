# 🎬 Pro Teleprompter Studio (Android Nativo)

Aplicativo Android nativo profissional de **Teleprompter & Estúdio de Gravação** desenvolvido em **Kotlin + Jetpack Compose**, com suporte a **IA Gemini Nano On-Device (AICore)**, gravação de vídeo **CameraX** integrada e layout adaptativo para **Celulares, Tablets, Dobráveis e Android TV**.

---

## 👨‍💻 Desenvolvedor
- **Autor:** Sandro Assis
- **Versão:** 1.0.0 (Build 100 • Broadcast Edition)
- **Plataforma:** Android Nativo (API 26+ até Android 16 / CompileSDK 36)

---

## ✨ Principais Recursos Profissionais

1. **Estúdio Teleprompter de Alta Precisão**
   - Rolagem fluida em 60/120fps calibrada em **WPM (Palavras Por Minuto)** (40 a 280 WPM).
   - **Pausas Programadas Automáticas (`[PAUSA 2s]`, `[PAUSA 5s]`)** com contagem regressiva visual quando a tag cruza a linha de leitura.
   - **Linha Guia de Leitura (Eye-Line Focus Guide)** com altura ajustável, máscara de foco superior/inferior e margens laterais configuráveis.
   - **Modo Espelho (Mirror Horizontal & Vertical)** para uso em equipamentos físicos com vidro refletor (*beam splitter*).
   - **Barra de Tópicos (`=== TÓPICO ===`)** para salto instantâneo entre blocos do roteiro.
   - **Janela Flutuante / Picture-in-Picture (PiP)** para leitura sobre outros apps de câmera, lives ou redes sociais.

2. **IA Gemini Nano On-Device (`com.google.mlkit:genai-prompt`)**
   - Criação e revisão de roteiros em linguagem natural (por texto ou comando de voz) diretamente no aparelho via **Google ML Kit GenAI Prompt API (AICore)**, com motor local inteligente de fallback offline.

3. **Gravação de Vídeo Integrada (CameraX)**
   - Visualização ao vivo da câmera **Frontal ou Traseira** sob o texto do teleprompter.
   - Gravação em MP4 Full HD com áudio salva diretamente na Galeria (`Movies/ProTeleprompter`) e grade de enquadramento (Regra dos Terços).

4. **Rolagem Guiada por Voz & Controles Físicos**
   - Acompanhamento de leitura em tempo real via `SpeechRecognizer` (`pt-BR`).
   - Suporte nativo a **Controle Remoto Bluetooth, Pedal USB/Bluetooth, Teclado Sem Fio, Botões de Volume e Controle de Android TV (D-Pad)**, com testador de teclas em tempo real.

5. **Design Adaptativo Multi-Tela**
   - Adaptação automática para **Celular**, **Tablet / Dobrável** (tela dividida *Split-Screen* e grade em 2 colunas) e **Android TV** (suporte `LEANBACK_LAUNCHER` e grade em 3 colunas).

---

## 📦 Como Compilar e Gerar o APK

```bash
./gradlew assembleDebug
```

O APK compilado é gerado automaticamente na raiz do projeto:
- `pro-teleprompter.apk`

