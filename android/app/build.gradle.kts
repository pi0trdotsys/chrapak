plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.chrapak"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.chrapak"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // Release-like build signed with the debug key, only for measuring CPU and battery on a phone.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litert)
    implementation(libs.androidx.health.connect)

    testImplementation(libs.junit)
}

// The YAMNet model is not in git (scripts/fetch-yamnet.sh downloads it). Without it the app still
// builds and runs, but on the energy gate alone, which cannot tell snoring from speech or a fan.
// That failure is silent on the phone, so an APK without the model must not be built at all.
val yamnetModel = layout.projectDirectory.file("src/main/assets/yamnet.tflite")
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    doFirst {
        check(yamnetModel.asFile.isFile) {
            "Missing ${yamnetModel.asFile}. Run android/scripts/fetch-yamnet.sh first."
        }
    }
}
