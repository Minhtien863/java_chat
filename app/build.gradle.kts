plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.androids.javachat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.androids.javachat"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            pickFirsts.add("META-INF/INDEX.LIST")
            pickFirsts.add("META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.tasks)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //firebase
// Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore") {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
    }
    implementation("com.google.firebase:firebase-auth")
    // Sửa Google Auth để dùng BOM
    implementation("com.google.auth:google-auth-library-oauth2-http:1.33.1")

    //Api
    implementation (libs.retrofit)
    implementation (libs.converter.gson)
    implementation (libs.gson)
    implementation (libs.okhttp)
    implementation("io.grpc:grpc-okhttp:1.68.0")
    implementation("io.grpc:grpc-protobuf-lite:1.68.0")
    implementation("io.grpc:grpc-stub:1.68.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Scalable size unit
    implementation(libs.sdp.android)
    implementation(libs.ssp.android)

    //Rounded imgView
    implementation(libs.roundedimageview)

    //Mutil
    implementation(libs.multidex)

    //security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}