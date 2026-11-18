plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vortessence.mahjong"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Own ID to install alongside upstream; namespace stays upstream's for easy merges
        applicationId = "com.formlessform.darkmahjong"
        // Android 13: AGSL RuntimeShader
        minSdk = 33
        targetSdk = 37
        versionCode = 33
        versionName = "1.4.20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // -PperfHooks=true (tools/deploy-to-phone.sh): adb can open fixed scenes for measuring
        buildConfigField("boolean", "PERF_HOOKS", (findProperty("perfHooks") == "true").toString())
        // -PsideBySide=true: own ID and name, so testing never touches the Play copy's games
        val sideBySide = findProperty("sideBySide") == "true"
        if (sideBySide) applicationIdSuffix = ".test"
        manifestPlaceholders["appLabel"] = if (sideBySide) "Dark Mahjong Test" else "Dark Mahjong"
    }

    // "play" adds Google's closed-source in-app update and billing libraries; "foss" (F-Droid,
    // source builds) has none. They differ only in AppUpdates and Tips
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("foss") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    dependenciesInfo {
        // For F-Droid
        includeInApk = false
        // Only Play gets the bundle (encrypted); lets Play Console flag vulnerable libraries
        includeInBundle = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric screenshot tests
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", "true")
                // PerfPreview: -PperfSet=<folder> names the render set, -PperfOff=<stages> turns
                // drawing stages off (as the test builds' perfOff)
                findProperty("perfSet")?.let { v -> it.systemProperty("perfSet", v) }
                findProperty("perfOff")?.let { v -> it.systemProperty("perfOff", v) }
                it.maxHeapSize = "3g"
                // Robolectric reaches into FileDescriptor internals, locked down on newer JDKs
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    "playImplementation"(libs.play.app.update)
    "playImplementation"(libs.play.billing)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
