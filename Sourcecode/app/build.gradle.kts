plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.anthroteacher.multihasher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anthroteacher.multihasher"
        minSdk = 28
        targetSdk = 35
        versionCode = 32
        versionName = "1.32"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    ndkVersion = "27.1.12297006"
    buildToolsVersion = "35.0.0"
}

dependencies {

    // Core and Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // NEW: Needed for collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    // NEW: Needed for by viewModels() delegate and ViewModel integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Compose BOM (Bill of Materials) - manages versions for Compose libraries
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI Toolkit
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // NEW: Required for Icons like Icons.Filled.PlayArrow, etc.
    implementation(libs.androidx.compose.material.icons.core) // Or libs.androidx.material.icons.core if defined that way
    implementation(libs.androidx.compose.material.icons.extended) // Or libs.androidx.material.icons.extended

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Use BOM for consistent test versions
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Your Native Library Module
    implementation(project(":sha3"))

}
