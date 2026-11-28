plugins {
    id("com.android.application")
}

// Version comes from the release tag: v1.6.0 -> versionName 1.6.0, versionCode 10600.
// The release workflow passes it as -Pcorkkeys.version=<tag>. Local builds get 0.0.0.
val tagVersion: String = (providers.gradleProperty("corkkeys.version").orNull ?: "0.0.0").removePrefix("v")
val versionParts: List<Int> = tagVersion.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
val computedVersionCode: Int = (versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]).coerceAtLeast(1)

val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "io.github.nullangst.corkkeys"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nullangst.corkkeys"
        minSdk = 29
        targetSdk = 37
        versionCode = computedVersionCode
        versionName = tagVersion
        resValue("string", "app_name", "CorkKeys")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // A test build installs next to the real keyboard, under its own name.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "CorkKeys debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Word lists are already gzipped; storing them again saves nothing.
    androidResources {
        noCompress += "gz"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
        }
    }

    lint {
        abortOnError = true
    }

    testOptions {
        // Unit tests run on the JVM against a stub android.jar: plain default values instead of
        // "Stub!" errors. The tests cover pure logic: layouts, geometry, shift, punctuation,
        // word lookup and swipe decoding.
        unitTests.isReturnDefaultValues = true
    }
}

// Test-only library: it never reaches the APK. CI fails the build if the release runtime
// classpath holds anything besides the Kotlin standard library (.github/scripts/check_runtime_deps.sh).
dependencies {
    testImplementation("junit:junit:4.13.2")
}
