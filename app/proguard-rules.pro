# Add project specific ProGuard rules here.

# --- Debugging & Safety Rules ---
# Disable obfuscation (renaming) to keep stack traces readable and safe for reflection.
# R8 will still perform Tree Shaking (removing unused code) and Resource Shrinking.
-dontobfuscate

# Keep line numbers and source file names for stack traces
-keepattributes SourceFile,LineNumberTable

# --- UniFFI / Rust Binding Rules ---
# Keep all generated UniFFI classes to prevent JNI failures.
-keep class uniffi.sync.** { *; }

# --- JNA Rules (Required by UniFFI's Android implementation) ---
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }

# --- Jetpack Compose ---
# R8 usually handles Compose well, but keep this if you see layout issues.
-keepattributes *Annotation*

# --- Data Models ---
# Keep Task model to avoid confusion in logs/debugging, though technically not required for manual JSON parsing.
-keep class com.example.tasks.data.Task { *; }

# --- Optimization ---
# Aggressive optimizations can sometimes break JNI. If crash, disable this.
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
