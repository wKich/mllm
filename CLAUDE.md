# CLAUDE.md

This file provides guidance for Claude Code when working with this Android project.

## Project Overview

**AI Chat** is a privacy-focused Android chat application written in Kotlin that connects to any OpenAI-compatible API (OpenAI, Ollama, OpenRouter, etc.). It stores all conversations locally in SQLite, encrypts API keys via Android Keystore, and supports real-time streaming responses with SSE.

- **Language**: Kotlin (100%)
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Repository pattern
- **Min SDK**: 26 (Android 8.0) | **Target/Compile SDK**: 34 (Android 14)

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test --stacktrace

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug

# Full build (test + assemble)
./gradlew build --stacktrace
```

**Requirements**: JDK 17, Android SDK 34, Android Studio Hedgehog (2023.1.1)+

## Project Structure

```
app/src/main/java/com/mllm/chat/
├── AIChatApplication.kt       # Hilt @HiltAndroidApp entry point
├── MainActivity.kt            # Compose NavHost with "chat" and "settings" routes
├── data/
│   ├── local/                 # Room database layer
│   │   ├── AppDatabase.kt     # DB definition + migrations (currently v3)
│   │   ├── ConversationDao.kt
│   │   ├── MessageDao.kt
│   │   ├── ProviderDao.kt
│   │   ├── ProviderEntity.kt
│   │   ├── SecurePreferences.kt  # EncryptedSharedPreferences wrapper
│   │   └── AppPreferences.kt     # Web search config
│   ├── remote/
│   │   ├── OpenAIApiClient.kt    # OkHttp + SSE streaming client
│   │   └── WebSearchClient.kt    # Web search API client
│   ├── repository/
│   │   └── ChatRepository.kt     # Single repository for all data access
│   └── model/
│       ├── ApiConfig.kt
│       ├── Conversation.kt
│       ├── Message.kt
│       ├── Provider.kt
│       └── OpenAIModels.kt       # Request/response data classes
├── di/
│   └── AppModule.kt              # Hilt @Module providing singletons
├── ui/
│   ├── chat/
│   │   ├── ChatScreen.kt         # Main chat UI
│   │   └── ChatViewModel.kt      # ChatUiState + business logic
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── components/               # Reusable Composables
│   │   ├── ChatInput.kt
│   │   ├── ConversationItem.kt
│   │   ├── MessageBubble.kt
│   │   └── NetworkIndicator.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── util/
    └── NetworkUtil.kt            # Connectivity monitoring via Flow
```

## Architecture

Data flows top-down:

```
Compose UI  →  ViewModel (StateFlow)  →  Repository  →  Room DAOs / OkHttp
```

- **State**: Each screen has a `UiState` data class held in a `StateFlow` in its `ViewModel`.
- **Coroutines**: All async work uses `viewModelScope` + `Dispatchers.IO` for DB/network.
- **Streaming**: `OpenAIApiClient` uses OkHttp SSE via `callbackFlow` for real-time tokens.
- **Tool calling**: Manual execution loop (max 5 iterations) in `ChatViewModel`.
- **DI**: Hilt singletons injected into `ViewModel`s and `Repository`.

## Database

Room database at version **3** with two applied migrations. Schema:

| Table | Key Columns |
|---|---|
| `conversations` | `id`, `title`, `createdAt`, `updatedAt` |
| `messages` | `id`, `conversationId` (FK), `role`, `content`, `reasoningContent`, `createdAt`, `isStreaming`, `isError` |
| `providers` | `id`, `name`, `baseUrl`, `apiKey`, `selectedModel`, `systemPrompt`, `temperature`, `maxTokens`, `availableModelsJson`, `isActive` |

When adding new columns or tables, always add a migration in `AppDatabase.kt` and bump `version`.

## Key Dependencies

| Category | Library | Version |
|---|---|---|
| Compose BOM | `androidx.compose:compose-bom` | `2023.10.01` |
| Navigation | `androidx.navigation:navigation-compose` | `2.7.6` |
| Hilt | `com.google.dagger:hilt-android` | `2.48.1` |
| Room | `androidx.room:room-runtime` | `2.6.1` |
| OkHttp | `com.squareup.okhttp3:okhttp` | `4.12.0` |
| OkHttp SSE | `com.squareup.okhttp3:okhttp-sse` | `4.12.0` |
| Gson | `com.google.code.gson:gson` | `2.10.1` |
| Markwon | `io.noties.markwon:core` | `4.6.2` |

Annotation processing uses **KSP** (not KAPT).

## CI/CD

GitHub Actions (`.github/workflows/build.yml`) triggers on:
- Push to `main` or `claude/**` branches
- Pull requests targeting `main`
- Manual dispatch

Steps: checkout → JDK 17 setup → Gradle cache → `./gradlew test` → `assembleDebug` → `assembleRelease` → upload APK artifacts (7-day retention).

## Coding Conventions

- Follow standard Kotlin style (`kotlin.code.style=official` in `gradle.properties`).
- Use `@HiltViewModel` + `@Inject constructor` for all ViewModels.
- Expose UI state as a single `data class UiState` via `StateFlow<UiState>`.
- Use `Dispatchers.IO` for all Room/network calls; collect on the UI thread.
- New DAOs must be registered in `AppDatabase` and provided in `AppModule`.
- Keep `Repository` as the single data-access point for ViewModels — no direct DAO access from UI layer.
- Cleartext HTTP is permitted (for local Ollama support) via `network_security_config.xml`.
- Never log API keys or sensitive user data.

## Testing

No test files currently exist. When adding tests:
- Unit tests go in `app/src/test/`
- Instrumented tests go in `app/src/androidTest/`
- Run with `./gradlew test` and `./gradlew connectedAndroidTest` respectively.
