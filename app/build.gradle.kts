// PocketShell application module.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.pocketshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.pocketshell"
        minSdk = 26
        // targetSdk 28 is a deliberate, documented decision (docs/M2-RESEARCH
        // §1.2, docs/CHANGELOG 0.3.1): Android's W^X policy neverallows
        // execute_no_trans on app_data_file for every untrusted-app SELinux
        // domain EXCEPT the legacy ones, and seapp_contexts maps targetSdk
        // 28 -> untrusted_app_27 (targetSdk 29 -> untrusted_app_29, blocked).
        // A proot guest shell can only exist in that legacy domain — the
        // exact tradeoff Termux makes. Side-load distribution; Play rules do
        // not apply (and Android 14+ still installs targetSdk >= 23).
        targetSdk = 28
        versionCode = 21
        versionName = "0.7.0-m3.4"
    }

    // v0.4.1: pin the debug signing key IN THE REPO. Lesson from sandbox reset
    // #5: ~/.android/debug.keystore was wiped and regenerated with a random
    // key, silently breaking update-installs over every shipped build (debug
    // APKs are side-loaded; their key IS the update identity). With the
    // keystore committed, every future build — on any machine — signs with
    // the same key and stays an in-place update. Debug-only key, no secrets.
    signingConfigs {
        getByName("debug") {
            val pinned = rootProject.file("keystore/debug.keystore")
            if (pinned.isFile) {
                storeFile = pinned
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Extract native libraries to the filesystem at install time.
            // Without this AGP sets extractNativeLibs=false and ships .so
            // files only inside the APK: System.loadLibrary still works,
            // but nativeLibraryDir stays EMPTY, so path-based execve() of
            // libproot.so is impossible. This was the v0.3.0 device crash:
            // "proot binary missing" thrown from the Home click handler.
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Vendored terminal engine (GPLv3) — see docs/THIRD_PARTY.md
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    // M2 runtime: safe tar.gz extraction of the Alpine minirootfs (Apache-2.0)
    implementation(libs.commons.compress)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
