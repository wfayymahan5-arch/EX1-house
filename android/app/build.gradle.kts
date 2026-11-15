plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ex1legend.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ex1legend.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("player") {
            dimension = "edition"
            applicationIdSuffix = ".player"
            resValue("string", "app_name", "EX1 LEGEND")
            buildConfigField("String", "START_URL", "\"https://ex1-house.vercel.app/app.html\"")
        }
        create("admin") {
            dimension = "edition"
            applicationIdSuffix = ".admin"
            resValue("string", "app_name", "EX1 LEGEND Admin")
            buildConfigField("String", "START_URL", "\"https://ex1-house.vercel.app/admin.html\"")
        }
    }

    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
