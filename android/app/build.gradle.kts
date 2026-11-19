plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ex1legend.app"
    compileSdk = 35
    defaultConfig {
        val ex1BaseUrl = providers.gradleProperty("ex1BaseUrl").orNull ?: "https://ex1-house.vercel.app"
        buildConfigField("String", "EX1_BASE_URL", "\"${ex1BaseUrl.trimEnd('/')}\"")
        applicationId = "com.ex1legend.app"
        minSdk = 23
        targetSdk = 35
        versionCode = providers.gradleProperty("ex1VersionCode").orNull?.toIntOrNull() ?: 5
        versionName = providers.gradleProperty("ex1VersionName").orNull ?: "2.2.0-native-preview"
        buildConfigField("String", "SUPABASE_URL", "\"https://wkyzhqygmppdppvfqtgf.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_j19HMoltMIILTNK3U1GL1A_RZzyv4zg\"")
    }
    buildFeatures { compose = true; buildConfig = true }
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
    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
            storeFile = keystorePath?.let { file(it) }
            storePassword = providers.environmentVariable("ANDROID_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
