import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.gavanhomes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.gavanhomes"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // release signing: the key and its passwords live in keystore.properties / the .jks next to this project (both git-ignored)
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // the build you run from Android Studio: your local Laravel server (http://127.0.0.1:8000 over `adb reverse`)
        // — a phone cannot resolve gavanhomes.test (it only exists in the PC's hosts file)
        debug {
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "GavanHomes")
            buildConfigField("String", "BASE_URL", "\"http://127.0.0.1:8000\"")
        }
        // the app you ship: the live site, https only and signed
        release {
            // ./gradlew assembleRelease -PsideBySide  → installs next to the real app, for testing the release build on your own phone
            if (project.hasProperty("sideBySide")) applicationIdSuffix = ".rel"
            resValue("string", "app_name", "GavanHomes")
            buildConfigField("String", "BASE_URL", "\"https://gavan.co.in\"")
            // no R8 shrinking: limiting it to library packages broke androidx.startup on launch (IllegalAccessError)
            optimization { enable = false }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}