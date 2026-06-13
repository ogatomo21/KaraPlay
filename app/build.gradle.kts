import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("net.syarihu.license-scribe-screen") version "0.3.1"
}

android {
    namespace = "net.ogatomo.karaplay"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.ogatomo.karaplay"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val localProps = rootProject.file("local.properties")
            if (localProps.exists()) {
                val props = Properties().apply { load(localProps.inputStream()) }
                storeFile = rootProject.file(props["storeFile"] ?: "")
                storePassword = props["storePassword"] as? String ?: ""
                keyAlias = props["keyAlias"] as? String ?: ""
                keyPassword = props["keyPassword"] as? String ?: ""
            } else {
                storeFile = file("keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }

        }
    }

    splits {
        abi {
            isEnable = true // 💡 ABI（CPU）ごとの分割ビルドを有効化
            reset()         // デフォルトのターゲットを一旦リセット

            // 出力したい個別のアーキテクチャを指定
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

            isUniversalApk = true // 💡 これを true にすると universal APK も同時に作られます
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.documentfile)
    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.license.scribe.runtime)
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

licenseScribeScreen {
    generatedPackageName.set("net.ogatomo.karaplay")
}