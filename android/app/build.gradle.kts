import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// "REPLACE THE CURRENT MAP IMPLEMENTATION" (2026-08-18): the Solar Site map now runs on
// MapLibre Native + OpenFreeMap (see the `map` package) — no API key, billing account, or
// local.properties entry required for the base map at all. local.properties itself is kept for
// the credential placeholders below (manufacturer APIs, AI) and the future satellite-imagery
// provider placeholder, none of which the base map depends on.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// A85 (Phase 23/24 — "NO PAID SERVICES / NO PRODUCTION API CREDENTIALS right now... use...
// environment-variable placeholders... Never hard-code secrets"): same local.properties pattern as
// mapsApiKey above, one property per credential the spec's own config block names. Every one
// defaults to "" (never committed, never hardcoded) — MonitoringConfig/AiConfig read these via
// BuildConfig and treat blank as "not configured," which is what routes every manufacturer to its
// MockMonitoringProvider and the AI layer to Disabled until real values are dropped into
// local.properties later. Add lines like `DEYE_API_KEY=...` to android/local.properties to activate.
fun localProp(key: String): String = localProperties.getProperty(key) ?: ""
val deyeApiKey = localProp("DEYE_API_KEY")
val deyeClientId = localProp("DEYE_CLIENT_ID")
val deyeClientSecret = localProp("DEYE_CLIENT_SECRET")
val luxPowerApiKey = localProp("LUXPOWER_API_KEY")
val growattApiKey = localProp("GROWATT_API_KEY")
val solarmanAppId = localProp("SOLARMAN_APP_ID")
val solarmanAppSecret = localProp("SOLARMAN_APP_SECRET")
val solarOfThingsApiKey = localProp("SOLAR_OF_THINGS_API_KEY")
val aiApiKey = localProp("AI_API_KEY")
// Satellite imagery (2026-08-18, provider chosen: MapTiler — see MapTilerSatelliteProvider's own
// doc): same build-now-activate-later pattern as the manufacturer-API keys above — blank by
// default, so SatelliteProvider stays NoSatelliteProvider (map view only, honestly labeled
// unavailable) until a real key is added to android/local.properties as MAPTILER_API_KEY.
val mapTilerApiKey = localProp("MAPTILER_API_KEY")
// 2026-08-19 ("do this google sign in/OAuth" — identity-capture only, see GoogleIdentityConfig's
// own doc): a "Web application" type OAuth 2.0 client ID from Google Cloud Console — NOT the
// "Android" type client (package name + SHA-1) that also has to exist in the same Cloud project
// for Google to recognize this app; only the Web client's ID is a value the app's code needs.
val googleWebClientId = localProp("GOOGLE_WEB_CLIENT_ID")

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

        buildConfigField("String", "DEYE_API_KEY", "\"$deyeApiKey\"")
        buildConfigField("String", "DEYE_CLIENT_ID", "\"$deyeClientId\"")
        buildConfigField("String", "DEYE_CLIENT_SECRET", "\"$deyeClientSecret\"")
        buildConfigField("String", "LUXPOWER_API_KEY", "\"$luxPowerApiKey\"")
        buildConfigField("String", "GROWATT_API_KEY", "\"$growattApiKey\"")
        buildConfigField("String", "SOLARMAN_APP_ID", "\"$solarmanAppId\"")
        buildConfigField("String", "SOLARMAN_APP_SECRET", "\"$solarmanAppSecret\"")
        buildConfigField("String", "SOLAR_OF_THINGS_API_KEY", "\"$solarOfThingsApiKey\"")
        buildConfigField("String", "AI_API_KEY", "\"$aiApiKey\"")
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerApiKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
        buildConfig = true
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

    // "REPLACE THE CURRENT MAP IMPLEMENTATION" (2026-08-18): the Solar Site map now renders on
    // MapLibre Native's Android SDK (not the JS/web "MapLibre GL JS" the request named — that
    // library is for browsers; this is the native Kotlin binding of the same open-source engine,
    // consuming the identical style-JSON format, so the OpenFreeMap "Liberty" style URL is used
    // unchanged — see com.lumix.estimator.map.OpenFreeMapProvider's own doc). Published to Maven
    // Central under org.maplibre.gl — no Google Maven repo dependency, no API key, no billing
    // account. Version below is believed current as of writing but, like the note this replaces,
    // could not be verified against Maven Central in this environment (network-restricted
    // sandbox) — bump to latest in Android Studio if Gradle reports a newer one available.
    implementation("org.maplibre.gl:android-sdk:11.8.1")
    // Device location for the "My Location" button — the Fused Location Provider, a distinct
    // Play Services module from Maps itself; no API key or billing account either.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 2026-08-19 ("do this google sign in/OAuth"): Credential Manager — Google's current
    // recommended "Sign in with Google" API on Android, superseding the older
    // com.google.android.gms.auth.api.signin.GoogleSignInClient. credentials-play-services-auth
    // is the Play-Services-backed implementation Credential Manager delegates to on real devices;
    // googleid provides GetSignInWithGoogleOption/GoogleIdTokenCredential. Versions believed
    // current as of writing but, like every other dependency version in this file, could not be
    // verified against Maven Central in this network-restricted sandbox — bump in Android Studio
    // if Gradle reports newer ones available.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

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
