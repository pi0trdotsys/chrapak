import java.util.Properties

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
        versionCode = 19
        versionName = "1.9.0"
    }

    // One key for every build, on every machine. Android refuses an update signed with a different
    // key, and each machine's own debug key made every switch between computers cost a reinstall and
    // the user's recordings. The key lives outside the repository; see android/README.md, "Podpis".
    val chrapakKey = chrapakSigningProperties()?.let { props ->
        signingConfigs.create("chrapak") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = chrapakKey
        }
        debug {
            // With the key at hand, debug builds can be installed over releases and the other way round.
            if (chrapakKey != null) signingConfig = chrapakKey
        }
        // Release-like build for measuring CPU and battery on a phone; signed like debug.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = chrapakKey ?: signingConfigs.getByName("debug")
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

/**
 * The release key's settings: the file named by CHRAPAK_SIGNING (a path), or else
 * ~/.chrapak-signing/keystore.properties. Null when neither exists.
 */
fun chrapakSigningProperties(): Properties? {
    val path = System.getenv("CHRAPAK_SIGNING") ?: "${System.getProperty("user.home")}/.chrapak-signing/keystore.properties"
    val file = File(path)
    if (!file.isFile) return null
    return Properties().apply { file.inputStream().use { load(it) } }
}

// A release APK signed with anything but the Chrapak key would not install over the existing app,
// so without the key the release build stops instead of quietly signing with something else.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        check(chrapakSigningProperties() != null) {
            "No release key. Put keystore.properties in ~/.chrapak-signing/ or point CHRAPAK_SIGNING at it (android/README.md, \"Podpis\")."
        }
    }
}
