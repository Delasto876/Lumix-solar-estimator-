import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// A81 (Phase 18): the Solar Site map screen needs a Google Maps API key. Read from
// local.properties (already git-ignored, never committed) rather than hardcoded, so each
// developer/installer drops in their own key: add a line `MAPS_API_KEY=your_key_here` to
// android/local.properties. Left blank, the app still builds and runs — only the map tiles
// themselves won't load; the manual site-entry flow needs no key at all.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.lumix.estimator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumix.estimator"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Recent androidx.core / androidx.lifecycle releases require compiling against Android API 37,
// which this project isn't on (compileSdk 34 above). These two versions are the only place that
// ceiling is defined — bump them together with compileSdk, deliberately, rather than letting one
// drift ahead of the other via an IDE "Update dependency" quick-fix on just one line below.
val androidxCoreVersion = "1.13.1"
val androidxLifecycleVersion = "2.8.4"

// Forces every transitive resolution of these two groups to the pinned versions above, so a
// library added later (or pulled in transitively by something like play-services-location) can't
// silently drag core/lifecycle past what compileSdk 34 supports and reintroduce the "requires
// compiling against Android API 37" AAR metadata error.
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core-ktx:$androidxCoreVersion",
            "androidx.core:core:$androidxCoreVersion",
            "androidx.lifecycle:lifecycle-runtime-ktx:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-viewmodel-compose:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-runtime-compose:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-runtime-compose-android:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-common:$androidxLifecycleVersion",
            "androidx.lifecycle:lifecycle-viewmodel:$androidxLifecycleVersion"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:$androidxCoreVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$androidxLifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$androidxLifecycleVersion")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // A81 (Phase 18): Solar Site satellite map + roof tracing (needs a MAPS_API_KEY in
    // local.properties to actually render tiles — see the comment near the top of this file)
    // and device location for the "My Location" button. Versions below are believed current as
    // of writing but couldn't be verified against Google's Maven repo in this environment
    // (network-blocked sandbox) — bump to latest in Android Studio if Gradle reports a newer
    // one available.
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    // A83 (Phase 22): SimulatedMonitoringProviderTest calls suspend fun fetchLatest via
    // runBlocking. kotlinx-coroutines-core is already pulled in transitively (Room/DataStore/
    // Lifecycle-ktx all depend on it), but declared explicitly here rather than relying on that,
    // consistent with how every other test dependency in this file is already explicit.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
