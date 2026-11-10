# Build and Run Guide

## Prerequisites

Ensure your development environment meets the following specifications:
- **Operating System:** Windows 10/11, macOS (Apple Silicon or Intel), or Linux x86_64.
- **Java Development Kit (JDK):** JDK 17 (OpenJDK or Azul Zulu).
- **Android SDK:** API Level 34 (Android 14) or API Level 35 (Android 15).
- **Android NDK:** Version `27.0.12077973` or newer (required for C++17 ARM NEON compilation).
- **CMake:** Version `3.22.1` or newer.
- **Gradle:** Wrapper included (8.14.3 with Kotlin Gradle Plugin 2.0.21).
- **Hardware (for on-device testing):**
  - Physical Android device with ARM64 (`arm64-v8a`) architecture.
  - Recommended RAM: 6GB+ for 3B/4B quantized models; 8GB+ for 7B/8B quantized models.

---

## 1. Local Build Commands

### Clean and Build
To assemble the debug APK:
```bash
./gradlew assembleDebug
```
The resulting APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Execute Test Suite
To run all JVM unit and integration tests across `:app` and `:llama-cpp-bridge`:
```bash
./gradlew testDebugUnitTest
```

### Build Minified / Release Package
To verify ProGuard / R8 code shrinking and native symbol preservation:
```bash
./gradlew assembleRelease
```

---

## 2. Model Preparation & Deployment

The local inference engine requires quantized GGUF model files compatible with `llama.cpp`.

### Recommended Quantized Models
- **SmolLM2-1.7B-Instruct (Q4_K_M):** ~1.0 GB RAM footprint. Excellent for rapid local tool calling and benchmarking.
- **Llama-3.2-3B-Instruct (Q4_K_M):** ~2.2 GB RAM footprint. Superior reasoning and structured UI generation.
- **Qwen2.5-3B-Instruct (Q4_K_M):** ~2.1 GB RAM footprint. Highly conformant JSON output for agent planning and A2UI streaming.

### Transferring Models to Device
Connect your Android device via USB with ADB debugging enabled:
```bash
# Push model directly to device internal storage
adb push smollm2-1.7b-instruct-q4_k_m.gguf /sdcard/Download/
```

Within the app:
1. Navigate to **Model Selection Screen** (`Screen.ModelSelection`).
2. Tap **Select Local Model File** and pick the pushed `.gguf` file via the system Storage Access Framework.
3. Configure context length (default: 2048 tokens) and thread count (recommended: 4 threads on big.LITTLE ARM cores).
4. Tap **Load Model**. The native bridge initializes the model into RAM.

---

## 3. Remote Engine Configuration (Optional)
If testing hybrid local-remote workflows or OpenAI-compatible backends:
1. Open **Settings Screen**.
2. Input Base URL (e.g., `https://api.openai.com/v1` or local Ollama endpoint `http://10.0.2.2:11434/v1`).
3. Enter API Key.
4. Set daily and monthly token budget limits in the **Token Budget Manager**.

