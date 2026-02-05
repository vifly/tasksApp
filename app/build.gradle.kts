plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.mozilla.rust-android-gradle.rust-android") version "0.9.6"
}

cargo {
    module = "../rust"
    libname = "sync"
    targets = listOf("arm64")
}

android {
    namespace = "com.example.tasks"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.example.tasks"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            java.srcDir(project.layout.buildDirectory.dir("generated/source/uniffi"))
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.material.icons)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

abstract class GenerateUniffiBindingsTask : DefaultTask() {
    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:InputDirectory
    abstract val rustJniLibsDir: DirectoryProperty

    @get:InputDirectory
    abstract val rustProjectDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val libPath = rustJniLibsDir.file("android/arm64-v8a/libsync.so").get().asFile.absolutePath

        execOperations.exec {
            workingDir = rustProjectDir.get().asFile
            commandLine = listOf(
                "cargo", "run", "--features", "uniffi/cli", "--bin", "uniffi-bindgen", "--",
                "generate", "--library", libPath,
                "--language", "kotlin", "--out-dir", outDir.get().asFile.absolutePath
            )
        }
    }
}

val generateUniffiBindings = tasks.register<GenerateUniffiBindingsTask>("generateUniffiBindings") {
    dependsOn("cargoBuild")

    rustJniLibsDir.set(project.layout.buildDirectory.dir("rustJniLibs"))
    rustProjectDir.set(project.layout.projectDirectory.dir("../rust"))
    outDir.set(project.layout.buildDirectory.dir("generated/source/uniffi"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateUniffiBindings)
}