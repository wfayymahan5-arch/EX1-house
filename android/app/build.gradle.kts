plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ex1legend.app"
    compileSdk = 35

    defaultConfig {
        val baseUrl = providers.gradleProperty("ex1BaseUrl").orNull ?: "https://ex1-house.vercel.app"
        val updateChannel = providers.gradleProperty("ex1UpdateChannel").orNull ?: "preview"
        require(updateChannel in listOf("preview", "stable")) { "ex1UpdateChannel must be preview or stable" }
        buildConfigField("String", "EX1_BASE_URL", "\""+baseUrl.trimEnd('/')+"\"")
        buildConfigField("String", "EX1_UPDATE_CHANNEL", "\""+updateChannel+"\"")
        applicationId = "com.ex1legend.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "2.3.0-$updateChannel"
    }

    buildFeatures { buildConfig = true }
    flavorDimensions += "edition"
    productFlavors {
        create("player") {
            dimension = "edition"
            applicationIdSuffix = ".$updateChannel.player"
            resValue("string", "app_name", "EX1 LEGEND")
            buildConfigField("boolean", "IS_ADMIN", "false")
        }
        create("admin") {
            dimension = "edition"
            applicationIdSuffix = ".$updateChannel.admin"
            resValue("string", "app_name", "EX1 LEGEND Admin")
            buildConfigField("boolean", "IS_ADMIN", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity:1.10.0")
}
