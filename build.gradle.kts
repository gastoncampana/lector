plugins {
    id("com.android.application") version "9.5.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

val releaseKey = file(System.getProperty("user.home")).resolve(".config/lector/release.p12")

android {
    namespace = "io.github.gastoncampana.lector"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gastoncampana.lector"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // sherpa-onnx ships native code for four ABIs; phones that can run its voices fast enough are all arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseKey.exists()) create("release") {
            storeFile = releaseKey
            storePassword = releaseKey.resolveSibling("password").readText()
            keyAlias = "lector"
            keyPassword = storePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the published builds' key, this machine's debug key: scripts/release.sh refuses to ship that.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    // The voice: scripts/fetch-voice.sh puts it in voice/, too big for git. Stored uncompressed, so it loads in place.
    sourceSets.getByName("main").assets.srcDir("voice")
    androidResources.noCompress += listOf("onnx", "bin")

    packaging {
        // sherpa-onnx's C and C++ APIs: the app uses only its JNI library.
        jniLibs.excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
    }

    // Per-app language picker: locales come from res/values* (the default's from res/resources.properties).
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")

    testImplementation("junit:junit:4.13.2")
}

// Without the voice the app builds fine and dies on its first word: sherpa-onnx exits when an asset is missing.
val voice = file("voice/kokoro/.sha256")
tasks.named("preBuild") {
    doFirst { check(voice.exists()) { "No voice: run scripts/fetch-voice.sh" } }
}
