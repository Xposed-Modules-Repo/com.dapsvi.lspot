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
}

dependencies {
    compileOnly(files("libs/xposed-api-stub.jar"))
}

// Task: produce SHA-256 checksum for the signed release APK
tasks.register("checksums") {
    dependsOn("assembleRelease")

    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").get().asFile
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(apk.readBytes())
            .joinToString("") { "%02x".format(it) }
        val shaFile = File(apk.parentFile, "app-release-unsigned.apk.sha256")
        println("Writing checksum to: ${shaFile.absolutePath}")
        shaFile.writeText("$sha256  ${apk.name}\n")
        println("File exists: ${shaFile.exists()}")
        println("Signed APK: ${apk.absolutePath}")
        println("SHA-256: $sha256")
    }
}
