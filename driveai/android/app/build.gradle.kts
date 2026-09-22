plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.driveai.cockpit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.driveai.cockpit"
        // API 28 = Android 9.0: cubre el grueso de las radios chinas en circulacion.
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Solo los recursos que usamos: cada idioma y densidad extra pesa.
        resourceConfigurations += setOf("es", "en")
        ndk { abiFilters += setOf("armeabi-v7a", "arm64-v8a") }
    }

    buildTypes {
        release {
            // R8 en modo completo: el APK de cabina baja a ~2.5 MB y el
            // heap inicial se reduce al eliminar clases muertas.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        // Sin ViewBinding ni Compose: la interfaz es el WebView.
        compose = false
        viewBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    // Deliberadamente minimo: cada libreria se paga en RAM durante el viaje.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")

    // Android Auto + Android Automotive OS (plantillas certificadas)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-automotive:1.4.0")
}
