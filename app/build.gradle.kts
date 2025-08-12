plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt") // ✅ 추가
}

android {
    namespace = "com.kimby.bycalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kimby.bycalendar"
        minSdk = 28
        targetSdk = 36
        versionCode = 2508121
        versionName = "25.08.121"
        buildConfigField(
            "String",
            "HOLIDAY_API_KEY",
            "\"" + (project.findProperty("HOLIDAY_API_KEY") ?: "") + "\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // ✅ Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.androidx.work.runtime.ktx)
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // ✅ Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // ✅ PhotoView
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // ✅ MVVM + Coroutine
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // ✅ Retrofit + Simple XML
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-simplexml:2.9.0")
    implementation("org.simpleframework:simple-xml:2.7.1") // ✅ 핵심!
}