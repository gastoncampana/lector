plugins {
    id("com.android.application") version "9.5.0"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the published builds' key, this machine's debug key: scripts/release.sh refuses to ship that.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Per-app language picker: locales come from res/values* (the default's from res/resources.properties).
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
