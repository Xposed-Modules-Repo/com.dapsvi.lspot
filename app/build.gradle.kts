import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "com.dapsvi.lspot"
    compileSdk = 34

    defaultConfig {
        minSdk = 33
        applicationId = "com.dapsvi.lspot"
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            storeFile = project.findProperty("keystorePath")?.let { File(it.toString()) }
            storePassword = project.findProperty("keystorePassword")?.toString()
            keyAlias = project.findProperty("keystoreAlias")?.toString()
            keyPassword = project.findProperty("keystoreAliasPassword")?.toString()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (project.findProperty("keystorePath") != null) {
                signingConfig = signingConfigs["release"]
            }
        }
    }

    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "app-release.apk"
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
        val apk = File(apkDir, "app-release.apk")

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val md5 = MessageDigest.getInstance("MD5")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }

        File(apkDir, "app-release.apk.sha256").writeText("$sha256  ${apk.name}\n")
        File(apkDir, "app-release.apk.md5").writeText("$md5  ${apk.name}\n")

        println("Signed APK: ${apk.absolutePath}")
        println("SHA-256: $sha256")
        println("MD5:     $md5")
    }
}
