import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.pitwall.parallel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pitwall.parallel"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-parallel"

        val parallelEmbeddedDefault =
            localProps.getProperty(
                "PITWALL_PARALLEL_USE_EMBEDDED_BRIDGE",
                localProps.getProperty("PITWALL_USE_EMBEDDED_BRIDGE", "true"),
            ).equals("true", ignoreCase = true)

        val llmModelPath = localProps.getProperty("PITWALL_LLM_MODEL_PATH", "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        buildConfigField("Boolean", "PITWALL_USE_EMBEDDED_BRIDGE", "$parallelEmbeddedDefault")
        buildConfigField("String", "PITWALL_LLM_MODEL_PATH", "\"$llmModelPath\"")

        val configuredApiBase = localProps.getProperty("PITWALL_API_BASE_URL", "http://10.0.2.2:8765")
            .trim().trimEnd('/')
        val effectiveApiBase = if (parallelEmbeddedDefault) {
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
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
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
    implementation("androidx.activity:activity:1.9.3")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.duckdb.jdbc)
}

tasks.register<Copy>("syncPwaDist") {
    val dist = rootProject.file("../src/pwa/dist")
    onlyIf { dist.exists() }
    from(dist)
    into(layout.projectDirectory.dir("src/main/assets/pwa-www"))
}

tasks.named("preBuild").configure { dependsOn("syncPwaDist") }
