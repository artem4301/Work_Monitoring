plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.workmonitoring"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.workmonitoring"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    viewBinding {
        enable = true
    }
}

configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:1.9.22",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22",
        "org.jetbrains.kotlin:kotlin-reflect:1.9.22"
    )
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.22"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.vision.common)
    implementation(libs.androidx.tools.core)
    implementation(libs.androidx.animation.core.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.firebase.firestore)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.github.steve1316:opencv-android-sdk:4.7.0")
    implementation("com.yandex.android:maps.mobile:4.5.0-full")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
}