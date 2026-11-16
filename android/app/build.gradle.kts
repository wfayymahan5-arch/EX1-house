plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ex1legend.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ex1legend.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "2.0.0-native-preview"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("player") {
            dimension = "edition"
            applicationIdSuffix = ".player"
            resValue("string", "app_name", "EX1 LEGEND")
            buildConfigField("boolean", "IS_ADMIN", "false")
        }
        create("admin") {
            dimension = "edition"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "EX1 LEGEND Admin")
            buildConfigField("boolean", "IS_ADMIN", "true")
        }
    }

    buildTypes {
        debug { }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
