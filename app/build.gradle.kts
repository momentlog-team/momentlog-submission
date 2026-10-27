plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "team.momentlog.app"
    compileSdk = 35
    useLibrary("android.car") // CarPropertyManager (차량 속도 PERF_VEHICLE_SPEED)

    defaultConfig {
        applicationId = "team.momentlog.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 에뮬레이터에서 PC의 서버(localhost:8090)에 붙을 때 주소. 실차·시연 환경에 맞게 바꾼다.
        buildConfigField("String", "SEAL_SERVER", "\"http://10.0.2.2:8090\"")
        // QR로 휴대폰에 넘기는 문서 페이지 주소. 실차·시연 환경에 맞게 바꾼다.
        buildConfigField("String", "DOCUMENT_BASE_URL", "\"http://10.0.2.2:8090/r/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 화면 이동
    implementation("androidx.navigation:navigation-compose:2.8.3")
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    // 서버 통신
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // QR 그리기
    implementation("com.google.zxing:core:3.5.3")
}
