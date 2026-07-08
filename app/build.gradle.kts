plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)   // <- REQUIRED
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.adminloyalty"
    compileSdk = 34

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.adminloyalty"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TODO(owner): set the real backend base URL. Split per build flavor (dev/staging/prod)
        // once those environments exist.
        buildConfigField("String", "API_BASE_URL", "\"https://TODO-set-backend-base-url/api/v1\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.arch.core.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // PieChart & LineChart
    implementation(libs.mpandroidchart)


    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.okhttp)

    implementation(libs.firebase.analytics)
}