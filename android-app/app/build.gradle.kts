import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.pitwall.paddock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pitwall.paddock"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Emulator → host: 10.0.2.2. Device on LAN: your machine IP.
        val base = localProps.getProperty("PITWALL_API_BASE_URL", "http://10.0.2.2:8765")
        buildConfigField("String", "PITWALL_API_BASE_URL", "\"$base\"")
        // Google Cloud Console → APIs & Services → Maps SDK for Android. Also set in local.properties as MAPS_API_KEY=...
        val mapsKey: String = localProps.getProperty("MAPS_API_KEY", "")
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
        // Optional: hosted pre-brief (Three.js / static) — WebView appends ?focus= & track=sonoma
        val webBrief = localProps.getProperty("WEB_BRIEF_BASE_URL", "")
        buildConfigField("String", "WEB_BRIEF_BASE_URL", "\"$webBrief\"")

        val useEmbeddedBridge = localProps.getProperty("PITWALL_USE_EMBEDDED_BRIDGE", "false")
            .equals("true", ignoreCase = true)
        val llmModelPath = localProps.getProperty("PITWALL_LLM_MODEL_PATH", "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val llmHttpBase = localProps.getProperty("PITWALL_LLM_HTTP_BASE_URL", "")
            .trim().trimEnd('/')
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val llmHttpModel = localProps.getProperty("PITWALL_LLM_HTTP_MODEL", "gemma-4-E2B-it").trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("Boolean", "PITWALL_USE_EMBEDDED_BRIDGE", "$useEmbeddedBridge")
        buildConfigField("String", "PITWALL_LLM_MODEL_PATH", "\"$llmModelPath\"")
        buildConfigField("String", "PITWALL_LLM_HTTP_BASE_URL", "\"$llmHttpBase\"")
        buildConfigField("String", "PITWALL_LLM_HTTP_MODEL", "\"$llmHttpModel\"")

        val configuredApiBase = localProps.getProperty("PITWALL_API_BASE_URL", "http://10.0.2.2:8765")
            .trim().trimEnd('/')
        val effectiveApiBase = if (useEmbeddedBridge) {
            "http://127.0.0.1:8765"
        } else {
            configuredApiBase
        }
        buildConfigField("String", "PITWALL_API_BASE_URL_EFFECTIVE", "\"$effectiveApiBase/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":pitwall-bridge-ktor"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.duckdb.jdbc)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
