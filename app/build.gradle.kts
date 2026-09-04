import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)   // <- REQUIRED
    alias(libs.plugins.hilt)
}

// Release signing credentials live in keystore.properties at the repo root, which is
// git-ignored (the keystore itself must never be committed). When the file is absent —
// CI, a fresh clone — the release build still assembles, just unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.adminloyalty"
    compileSdk = 34

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        // Play rejects com.example.*, and applicationId is immutable once published.
        // Decoupled from `namespace` on purpose: the Java sources keep their original
        // package, only the shipped identity changes.
        applicationId = "com.beanloyal.admin"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Split per build flavor (dev/staging/prod) once those environments exist.
        buildConfigField("String", "API_BASE_URL", "\"https://bean-backend-ejzg.onrender.com/api/v1\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    testImplementation(libs.arch.core.testing)
    // Real org.json on the unit-test classpath — the android.jar stub throws "not mocked".
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // PieChart & LineChart
    implementation(libs.mpandroidchart)


    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.okhttp)

    implementation(libs.firebase.analytics)
}
