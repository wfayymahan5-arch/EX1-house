plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val updateChannel = providers.gradleProperty("ex1UpdateChannel").orNull ?: "preview"
require(updateChannel in listOf("preview", "stable")) { "ex1UpdateChannel must be preview or stable" }

android {
    namespace = "com.ex1legend.app"
    compileSdk = 35

    defaultConfig {
        val baseUrl = providers.gradleProperty("ex1BaseUrl").orNull ?: "https://ex1-house.vercel.app"
        buildConfigField("String", "EX1_BASE_URL", "\""+baseUrl.trimEnd('/')+"\"")
        buildConfigField("String", "EX1_UPDATE_CHANNEL", "\""+updateChannel+"\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://wkyzhqygmppdppvfqtgf.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_j19HMoltMIILTNK3U1GL1A_RZzyv4zg\"")
        applicationId = "com.ex1legend.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "2.3.0-$updateChannel"
    }

    buildFeatures { compose = true; buildConfig = true }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
