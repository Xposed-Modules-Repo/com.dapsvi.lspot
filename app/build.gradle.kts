import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "com.dapsvi.lspot"
    compileSdk = 34

    defaultConfig {
        minSdk = 27
        targetSdk = 33
        applicationId = "com.dapsvi.lspot"
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            val customKeystore = project.findProperty("keystorePath")
            if (customKeystore != null) {
                storeFile = File(customKeystore.toString())
                storePassword = project.findProperty("keystorePassword")?.toString()
                keyAlias = project.findProperty("keystoreAlias")?.toString()
                keyPassword = project.findProperty("keystoreAliasPassword")?.toString()
            } else {
                // Fall back to Android's debug keystore
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "debug"
                keyPassword = "android"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs["release"]
        }
    }

    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "LSpot-v${versionName}-signed.apk"
            }
        }
    }
}

dependencies {
    compileOnly(files("libs/xposed-api-stub.jar"))
}

// Task: produce SHA-256 + MD5 checksums for the signed release APK
tasks.register("checksums") {
    dependsOn("assembleRelease")

    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val versionName = android.defaultConfig.versionName
        val apk = File(apkDir, "LSpot-v${versionName}-signed.apk")

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val md5 = MessageDigest.getInstance("MD5")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }

        File(apkDir, "${apk.name}.sha256").writeText("$sha256  ${apk.name}\n")
        File(apkDir, "${apk.name}.md5").writeText("$md5  ${apk.name}\n")

        println("Signed APK: ${apk.absolutePath}")
        println("SHA-256: $sha256")
        println("MD5:     $md5")
    }
}
