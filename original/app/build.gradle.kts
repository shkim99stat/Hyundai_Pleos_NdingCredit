import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localMapSettings = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun mapSetting(name: String, fallback: String = "") = (providers.gradleProperty(name).orNull ?: System.getenv(name) ?: localMapSettings.getProperty(name) ?: fallback).trim()
fun quotedSetting(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    buildFeatures { buildConfig = true }
    useLibrary("android.car", false)
    namespace = "com.example.pleos_endingcredit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleos_endingcredit"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KAKAO_MAP_JS_KEY", quotedSetting(mapSetting("KAKAO_MAP_JS_KEY")))
        buildConfigField("String", "KAKAO_MAP_ORIGIN", quotedSetting(mapSetting("KAKAO_MAP_ORIGIN", "https://localhost")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.zxing:core:3.5.4")
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.speech.to.text)
    implementation(libs.pleos.text.to.speech)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
