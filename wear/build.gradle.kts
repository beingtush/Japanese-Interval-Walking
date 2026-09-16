plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.premkumar.jiwtracker.wear"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Same applicationId as the phone app. The Data Layer only pairs a phone and watch app
    // that share BOTH a package name and a signing certificate — see docs/WEAR-SIDELOAD.md.
    applicationId = "com.premkumar.jiwtracker"
    minSdk = 30 // Wear OS 3+
    targetSdk = 36
    versionCode = 7
    versionName = (project.findProperty("versionName") as String?) ?: "2.3.0"
  }

  // Intentionally no product flavors. The watch app is not distributed via F-Droid, and a
  // `provider` dimension here would double every task for no benefit.

  val keystorePathFromEnv = System.getenv("KEYSTORE_PATH")
  val storePasswordFromEnv = System.getenv("STORE_PASSWORD")
  val keyAliasFromEnv = System.getenv("KEY_ALIAS")
  val keyPasswordFromEnv = System.getenv("KEY_PASSWORD")
  val hasSigningEnv = keystorePathFromEnv != null && storePasswordFromEnv != null &&
    keyAliasFromEnv != null && keyPasswordFromEnv != null

  signingConfigs {
    create("release") {
      if (hasSigningEnv) {
        storeFile = file(keystorePathFromEnv)
        storePassword = storePasswordFromEnv
        keyAlias = keyAliasFromEnv
        keyPassword = keyPasswordFromEnv
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      if (hasSigningEnv) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      // Must match :app's debug suffix exactly. The Data Layer only pairs a phone and watch app
      // sharing a package name AND a signing certificate, so the debug pair forms its own pair
      // alongside any installed release build.
      applicationIdSuffix = ".debug"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":wear-protocol"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)

  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.androidx.wear.ongoing)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.wear.compose.ui.tooling)

  testImplementation(libs.junit)
}
