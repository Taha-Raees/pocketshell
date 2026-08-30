// Vendored from termux-app terminal-view/build.gradle (GPLv3).
// Pinned upstream commit: 3b66f8799635a4dba4a206563048ff0e6792c487 (docs/THIRD_PARTY.md).
// Ported from Groovy to Kotlin DSL; publishing tasks dropped; Java target 1.8 -> 17.
// Upstream Java/XML sources are byte-identical to upstream.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.view"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    api(project(":terminal-emulator"))
}
