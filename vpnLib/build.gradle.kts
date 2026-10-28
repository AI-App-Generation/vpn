/*
 * Veil VPN :vpnLib — vendored ics-openvpn v0.7.65 (https://github.com/schwabe/ics-openvpn)
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. See LICENSE.txt.
 *
 * Changes vs upstream `main` module: converted to com.android.library, only flavors
 * `skeleton` (dimension implementation) + `ovpn2` (dimension ovpnimpl) are kept,
 * OpenVPN3/swig, mbedTLS, asio, fmt and the UI flavor are removed.
 * Kotlin (AppCustomControl.kt) is compiled by AGP 9 built-in Kotlin (no kotlin-android plugin).
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "de.blinkt.openvpn"
    compileSdk = 37
    // Upstream v0.7.65 pins NDK 30.0.14904198; 28.2 is the newest installed and builds it.
    ndkVersion = "28.2.13676358"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    flavorDimensions += listOf("implementation", "ovpnimpl")
    productFlavors {
        create("skeleton") {
            dimension = "implementation"
        }
        create("ovpn2") {
            dimension = "ovpnimpl"
            buildConfigField("boolean", "openvpn3", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
}
