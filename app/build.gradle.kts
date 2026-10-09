import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Gitignored file at repo root; env vars win so CI can override. Same key
// names as the other mesmerverse apps.
val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStorePath = signingValue("ANDROID_APK_KEYSTORE_PATH")
val releaseStorePassword = signingValue("ANDROID_APK_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("ANDROID_APK_KEY_ALIAS")
val releaseKeyPassword = signingValue("ANDROID_APK_KEY_PASSWORD")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.mesmerverse.weatherballoon"
    compileSdk { version = release(36) }

    // Disable dependency metadata in APK signing block (required for F-Droid)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.mesmerverse.weatherballoon"
        minSdk = 34
        targetSdk = 36
        versionCode = 5
        versionName = "1.2"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.datastore.preferences)
}
