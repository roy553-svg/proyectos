# El puente JS <-> nativo se invoca por reflexion desde el WebView:
# si R8 lo renombra, el cockpit pierde voz y TTS.
-keepclassmembers class com.driveai.cockpit.MainActivity$NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# El host automotriz instancia estas clases por nombre desde el manifiesto.
-keep class com.driveai.cockpit.DriveAiCarAppService { *; }
-keep class com.driveai.cockpit.DriveAiSession { *; }
-keep class com.driveai.cockpit.SteeringWheelReceiver { *; }

# org.json viene del sistema; no lo empaquetamos ni lo ofuscamos.
-dontwarn org.json.**
