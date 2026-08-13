import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Both build types point at the deployed HTTPS backend, so a USB-connected device needs no
// local networking set up — no adb reverse, no LAN IP, no cleartext exception.
// Retrofit requires the trailing slash.
val backendUrl = "https://bookmarks-api-4i5h.onrender.com/"

/**
 * Release signing, if the key is present. `keystore.properties` and the keystore itself are
 * git-ignored, so a fresh clone has neither — and rather than failing the build, the release type
 * simply comes out unsigned, which is what CI wants anyway. The signed APK is the one attached to
 * the GitHub release.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasSigningKey = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

android {
    namespace = "az.bookmarks"
    compileSdk = 36

    defaultConfig {
        applicationId = "az.bookmarks"
        // minSdk 26 so the launcher icon can be adaptive-only — no legacy PNG densities to ship.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
        }
        release {
            // Both build types point at the same deployed backend, so the APK a reviewer installs
            // talks to the same API as the development build — never localhost.
            buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
            signingConfig = signingConfigs.findByName("release")
            // ponytail: no minification. R8 on a nine-screen app saves a few hundred kilobytes
            // and buys a class of release-only crash from a missing keep rule for the
            // serialization and Retrofit reflection. Turn it on when the APK size matters.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // collectAsStateWithLifecycle lives here. It arrives transitively via navigation-compose
    // today; declared so a navigation bump cannot take it away.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    // Arrives transitively with Retrofit, declared because Network.kt configures the client.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // Needed to drive viewModelScope: Dispatchers.Main does not exist in a JVM unit test, and
    // the paging races are only reachable by controlling when each response lands.
    testImplementation(libs.kotlinx.coroutines.test)
}
