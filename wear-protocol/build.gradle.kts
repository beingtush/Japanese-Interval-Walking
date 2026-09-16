plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.premkumar.jiwtracker.wearprotocol"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  // Exposed as `api` so both :app and :wear can build DataMap payloads without
  // re-declaring the dependency.
  api(libs.play.services.wearable)

  testImplementation(libs.junit)
}
