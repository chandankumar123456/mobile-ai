# Mobile AI Assistant — Native Android Local LLM Template

A clean, extensible, native Android application template for on-device Local LLM inference powered by the official **llama.cpp** Android integration.

Built with **Kotlin**, **Jetpack Compose (Material 3)**, **Android NDK**, **CMake**, and **JNI**.

---

## Highlights

- **Embedded Native llama.cpp**: Adapts the official `llama.cpp` Android example (`examples/llama.android`) and JNI integration (`ai_chat.cpp`). No external servers, no Python runtime, no HTTP proxies.
- **Dynamic Multi-Variant CPU Acceleration**: Automatically builds and packages hardware-optimized kernels (ARMv8.0 through ARMv9.2, Arm KleidiAI, x86-64 AVX2/AVX-512) and selects the fastest kernel at runtime based on host CPU features.
- **Storage Access Framework (SAF) Model Management**: Pick any `.gguf` file from device storage, validate GGUF v3 magic bytes, inspect architecture and quantization metadata without loading tensors, verify storage quota, and import into private app storage.
- **Real-Time Token Streaming**: True token-by-token generation streamed via Kotlin `Flow`, with UTF-8 multi-byte boundary protection and immediate cancellation support.
- **Clean Architecture & MVVM**: Strictly decoupled presentation (Jetpack Compose), domain (use cases, vendor-neutral `LocalInferenceEngine` interface), data repositories, and native adapter layers.
- **Material 3 Design**: Professional, minimal, polished chat interface with status pills, multiline input, auto-scrolling conversation history, and inference settings controls.

---

## Project Structure

```
Mobile AI/
├── app/                                 # Jetpack Compose application module
│   ├── src/main/java/com/mobileai/assistant/
│   │   ├── MainActivity.kt              # App entry point & dependency wiring
│   │   ├── domain/                      # Clean Architecture Domain Layer
│   │   │   ├── model/                   # ChatMessage, ModelInfo, InferenceSettings, EngineState
│   │   │   ├── engine/                  # LocalInferenceEngine interface
│   │   │   ├── repository/              # ModelRepository, ChatRepository interfaces
│   │   │   └── usecase/                 # LoadModel, GenerateResponse, CancelGeneration, etc.
│   │   ├── data/                        # Data & Native Adapter Layer
│   │   │   ├── file/                    # ModelFileManager (SAF streaming, storage checks)
│   │   │   ├── repository/              # ModelRepositoryImpl, ChatRepositoryImpl
│   │   │   └── native_engine/           # LlamaCppInferenceEngine (bridges to llama.cpp)
│   │   └── presentation/                # Jetpack Compose Presentation Layer (M3)
│   │       ├── chat/                    # ChatScreen, ChatViewModel, ChatUiState
│   │       ├── model/                   # ModelSelectionScreen, ModelSelectionViewModel
│   │       ├── settings/                # SettingsScreen, SettingsViewModel
│   │       ├── components/              # MessageBubble, ModelStatusBar, MetadataTag
│   │       ├── navigation/              # AppNavGraph, Screen routes
│   │       └── theme/                   # Theme, Color, Type
│   └── src/main/res/                    # Drawables, mipmaps, values
├── llama-cpp-bridge/                    # Official llama.cpp Android library module
│   ├── src/main/cpp/
│   │   ├── CMakeLists.txt               # NDK CMake build script
│   │   ├── ai_chat.cpp                  # Official JNI bridge (InferenceEngineImpl)
│   │   └── logging.h                    # Android logcat GGML bridge
│   └── src/main/java/com/arm/aichat/
│       ├── AiChat.kt                    # Facade factory
│       ├── InferenceEngine.kt           # Native engine interface & state machine
│       ├── internal/InferenceEngineImpl.kt # Single-threaded JNI coroutine wrapper
│       └── gguf/                        # Pure-Kotlin GgufMetadataReader parser
├── llama.cpp/                           # Official upstream llama.cpp source submodule
├── docs/
│   ├── ARCHITECTURE.md                  # Comprehensive architectural overview & diagrams
│   ├── LLAMA_CPP_INTEGRATION.md         # JNI bridge details, CMake mechanics, memory lifecycle
│   └── BUILD_AND_RUN.md                 # Step-by-step build, installation & model running guide
├── gradle/                              # Version catalog (libs.versions.toml) & wrapper
├── build.gradle.kts                     # Root build configuration
├── settings.gradle.kts                  # Project modules definition
└── gradlew.bat / gradlew                # Gradle wrapper scripts (v8.14.3)
```

---

## Upstream Integration Details

| Attribute | Specification |
|---|---|
| **Upstream Repository** | [https://github.com/ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) |
| **Commit Hash** | `f477f795cb071baf5b8af6488378366a72034ac4` |
| **Upstream Reference** | `docs/android.md` & `examples/llama.android` |
| **Native Bridge Library** | `libai-chat.so` linking `libllama.so`, `libllama-common.so`, `libggml.so` |
| **Supported ABIs** | `arm64-v8a` (hardware phones), `x86_64` (emulators) |
| **Kernel Optimizations** | KleidiAI, DotProd, FP16, SME, SVE (ARM) / AVX2, AVX-512 (x86_64) |

---

## Getting Started

### Prerequisites
- **JDK 17** (e.g. `C:\Program Files\Java\jdk-17`)
- **Android SDK** (API 34/35)
- **Android NDK** (`27.1.12297006`)
- **CMake** (`3.22.1`)

### Build from Command Line (PowerShell)

```powershell
# Set Java 17
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# Build debug APK
.\gradlew.bat assembleDebug

# Run unit tests
.\gradlew.bat test
```

The compiled APK will be output at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Recommended Models for Mobile Testing

Transfer any standard `.gguf` file to your device via USB or download via browser:

- **Qwen 2.5 0.5B Instruct (Q4_0 / Q4_K_M)**: ~350 MB download, fast and lightweight.
- **Qwen 2.5 1.5B Instruct (Q4_K_M)**: ~980 MB download, excellent reasoning-to-size ratio.
- **Llama 3.2 1B Instruct (Q4_K_M)**: ~750 MB download, solid general instruction following.
- **SmolLM2 360M Instruct (Q4_K_M)**: ~230 MB download, ultra-compact for low-RAM devices.

---

## Documentation Links

- [Architecture Guide](file:///E:/Projects/Mobile%20AI/docs/ARCHITECTURE.md)
- [llama.cpp Integration Deep Dive](file:///E:/Projects/Mobile%20AI/docs/LLAMA_CPP_INTEGRATION.md)
- [Build and Run Walkthrough](file:///E:/Projects/Mobile%20AI/docs/BUILD_AND_RUN.md)

---

## License

This project incorporates components from the `llama.cpp` project under the MIT License.
Copyright (c) 2023-2026 The ggml authors / Arm Limited.

