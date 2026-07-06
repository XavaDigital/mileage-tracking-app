plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xavadigital.mileagetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xavadigital.mileagetracker"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // OAuth client ids (public, not secrets). Neither is referenced by the
        // Play Services authorization flow: the Android client is matched by
        // package name + signing SHA-1; the web client would only be needed as
        // serverClientId for Google ID-token sign-in. Recorded here as the
        // single source of truth for the Google Cloud project config.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"273765857524-837m333nlq58hl8ccmksnpir2ghjhs4d.apps.googleusercontent.com\""
        )
        buildConfigField(
            "String",
            "GOOGLE_ANDROID_CLIENT_ID",
            "\"273765857524-b64np3p355kvmq551n94anekmfcmfbdr.apps.googleusercontent.com\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
}
