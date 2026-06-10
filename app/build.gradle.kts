plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.caminerin.backingtrack"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "guitartrainer"
            keyAlias = "guitartrainer"
            keyPassword = "guitartrainer"
        }
    }

    defaultConfig {
        applicationId = "com.caminerin.backingtrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        // Read-only token used to download audio assets from the (private)
        // GitHub release. Injected at build time from the Gradle property
        // `assetToken` or the env var ASSET_READ_TOKEN (set as a CI secret).
        // Defaults to empty for local builds; never commit a real token.
        val assetToken = (project.findProperty("assetToken") as String?)
            ?: System.getenv("ASSET_READ_TOKEN") ?: ""
        buildConfigField("String", "ASSET_TOKEN", "\"$assetToken\"")
        buildConfigField("String", "ASSET_REPO", "\"Caminerin/BackingTrack-Generator\"")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // enforcedPlatform forces every androidx.compose.* artifact to the BOM
    // version, preventing a transitive dependency from bumping one Compose
    // library (e.g. animation-core) out of sync with material3 at runtime.
    val composeBom = enforcedPlatform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
