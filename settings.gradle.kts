pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK (Vehicle, NaviHelper, TTS, STT, FusedLocation)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "momentlog"

include(":core")
include(":server")

// 차량 앱은 Android SDK가 있는 PC에서만 포함한다.
// (local.properties의 sdk.dir 또는 ANDROID_HOME). SDK가 없어도 core·server는 빌드·테스트된다.
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasAndroidSdk = localProps.getProperty("sdk.dir") != null ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
} else {
    println("[momentlog] Android SDK가 없어 :app 모듈을 건너뜁니다 (core, server만 빌드).")
}
