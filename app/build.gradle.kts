plugins {
    id("com.android.application")
}

android {
    namespace = "com.dapsvi.lspot"
    compileSdk = 34

    defaultConfig {
        minSdk = 33
        applicationId = "com.dapsvi.lspot"
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    compileOnly(files("libs/xposed-api-stub.jar"))
}
