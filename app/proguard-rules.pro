# ============================================================================
# Mobile AI Assistant — ProGuard / R8 Release Hardening Rules
# ============================================================================

# 1. Native C++ / JNI Interface Preservation
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.arm.aichat.** { *; }
-keepclassmembers class com.arm.aichat.** { *; }

# 2. Kotlinx Serialization Polymorphic Discriminators & Serializers
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
    public static *** Companion;
}
-keepclasseswithmembers class * {
    public static *** Companion;
}
-keepclassmembers class * {
    public static *** serializer(...);
}

# 3. Domain Model Preservation for Reflection/Polymorphic Serialization
-keep class com.mobileai.assistant.domain.genui.** { *; }
-keep class com.mobileai.assistant.domain.model.** { *; }
-keep class com.mobileai.assistant.domain.dot.** { *; }
-keep class com.mobileai.assistant.domain.task.** { *; }
-keep class com.mobileai.assistant.domain.tool.** { *; }
-keep class com.mobileai.assistant.domain.error.** { *; }
-keep class com.mobileai.assistant.domain.mcp.** { *; }
-keep class com.mobileai.assistant.domain.a2ui.** { *; }
-keep class com.mobileai.assistant.domain.openui.** { *; }
-keep class com.mobileai.assistant.domain.benchmark.** { *; }
-keep class com.mobileai.assistant.domain.evaluation.** { *; }
-keep class com.mobileai.assistant.domain.trace.** { *; }

# 4. Coroutines & Flow Obfuscation Protection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# 5. Jetpack Compose Rule
-keep class androidx.compose.runtime.** { *; }
