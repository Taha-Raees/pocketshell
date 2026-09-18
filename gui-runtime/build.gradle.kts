// PocketShell GUI runtime — mc Wayland kiosk compositor as an in-process
// native service (P3). ARM64 only for v1: the prebuilt wayland/xkbcommon/ffi
// set ships under jniLibs/arm64-v8a (same vendor pattern as libproot.so).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pocketshell.guirt"
    compileSdk = 36
    ndkVersion = System.getenv("PS_LOCAL_NDK") ?: "28.2.13676358"

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            ndkBuild {
                cFlags += listOf("-std=c11", "-Wall", "-Wextra", "-O2")
            }
        }

        // v1 gate: the GUI runtime is arm64-v8a only (matches the guest).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}
