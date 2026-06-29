plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Optional release signing, driven by environment variables. When unset (the default,
// e.g. a plain local build), the release build is simply left unsigned and the debug
// build — signed with the standard debug key — remains installable. CI populates these
// from repository secrets to produce a properly signed release. See README → Publishing.
val releaseStoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.pearl.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pearl.keyboard"
        minSdk = 29          // Android 10
        targetSdk = 34       // Android 14
        versionCode = 1
        versionName = "1.0"

        // Render vector drawables on all supported API levels.
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Keep false for an easy first build; flip on once you add real obfuscation rules.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release signing config only when a keystore was provided.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)

    // Proper emoji rendering on older devices + EmojiTextView for the emoji panel.
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)

    // Grids for the emoji / clipboard / GIF panels.
    implementation(libs.androidx.recyclerview)
}
