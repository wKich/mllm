# AI Chat - Android OpenAI-Compatible Chat App

A simple, privacy-focused Android chat application that connects to OpenAI-compatible APIs.

## Features

### Core Features
- **API Configuration**: Connect to any OpenAI-compatible endpoint (OpenAI, Ollama, OpenRouter, etc.)
- **Secure Storage**: API keys stored encrypted using Android Keystore
- **Streaming Responses**: Real-time token display with typing indicator
- **Markdown Rendering**: Support for code blocks, lists, bold/italic, tables
- **Conversation History**: Local SQLite storage with auto-generated titles
- **Offline Indicator**: Visual feedback when disconnected

### Chat Interface
- Standard message bubble UI (user right-aligned, AI left-aligned)
- Auto-expanding input field with send button
- Copy and share functionality for messages
- Pull-to-clear gesture support
- Error handling with retry option

### Settings
- Custom base URL input
- Secure API key management
- Model selection (presets + custom)
- Connection test button
- System prompt configuration
- Temperature slider (0.0-1.0)
- Max tokens setting

## Architecture

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **DI**: Hilt
- **Database**: Room
- **Network**: OkHttp with SSE streaming
- **Security**: EncryptedSharedPreferences

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build Steps

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on device/emulator

```bash
./gradlew assembleDebug
```

## Usage

1. Launch the app
2. Go to Settings
3. Configure your API:
   - **Base URL**: Your API endpoint (e.g., `https://api.openai.com/v1`)
   - **API Key**: Your API key
   - **Model**: Select or type model name (e.g., `gpt-4`, `llama3`)
4. Test connection
5. Save and start chatting

### Supported Endpoints

- OpenAI: `https://api.openai.com/v1`
- Ollama: `http://localhost:11434/v1`
- OpenRouter: `https://openrouter.ai/api/v1`
- Any OpenAI-compatible API

## Privacy

- All data stored locally on device
- API keys encrypted at rest
- No cloud sync or analytics
- No user accounts required

## Project Structure

```
app/src/main/java/com/mllm/chat/
├── data/
│   ├── local/          # Room database, DAOs, secure storage
│   ├── remote/         # OpenAI API client with streaming
│   ├── repository/     # Data repository
│   └── model/          # Data models
├── di/                 # Hilt dependency injection
├── ui/
│   ├── chat/           # Chat screen and ViewModel
│   ├── settings/       # Settings screen and ViewModel
│   ├── components/     # Reusable UI components
│   └── theme/          # Material 3 theming
├── util/               # Utilities (network monitoring)
├── AIChatApplication.kt
└── MainActivity.kt
```

## License

MIT License
