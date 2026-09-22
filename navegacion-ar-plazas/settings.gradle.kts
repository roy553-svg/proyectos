pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "navegacion-ar-plazas"

// :nucleo -> logica de dominio pura en Kotlin/JVM (modelo, grafo, A*, rutas,
//            posicionamiento matematico, QR). No depende de Android.
// :app    -> aplicacion Android (Compose, ARCore, CameraX, ML Kit).
include(":nucleo")

// El modulo :app solo se incluye si hay un Android SDK disponible.
// Android Studio genera local.properties automaticamente, y en CI se define
// ANDROID_HOME / ANDROID_SDK_ROOT. De esta forma `gradle :nucleo:test` funciona
// tambien en maquinas sin Android SDK instalado.
val haySdkAndroid = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null

if (haySdkAndroid) {
    include(":app")
} else {
    logger.lifecycle(
        "[navegacion-ar-plazas] Android SDK no detectado: se omite el modulo :app. " +
            "Abre el proyecto en Android Studio o define ANDROID_HOME para compilar la app."
    )
}
