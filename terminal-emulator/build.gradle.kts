// Vendored from termux-app terminal-emulator/build.gradle (GPLv3).
// Pinned upstream commit: 3b66f8799635a4dba4a206563048ff0e6792c487 (docs/THIRD_PARTY.md).
// Ported from Groovy to Kotlin DSL; publishing tasks dropped; Java target 1.8 -> 17.
// Upstream Java/C sources are byte-identical to upstream.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.emulator"
    compileSdk = 36
    // PS_LOCAL_NDK lets an aarch64-host local build select a community NDK
    // (e.g. lzhiyong/termux-ndk) without touching the CI pin. Unset = CI pin.
    ndkVersion = System.getenv("PS_LOCAL_NDK") ?: "28.2.13676358"

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            ndkBuild {
                // Flags identical to upstream build.gradle
                cFlags += listOf(
                    "-std=c11", "-Wall", "-Wextra", "-Werror", "-Os",
                    "-fno-stack-protector", "-Wl,--gc-sections"
                )
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}
