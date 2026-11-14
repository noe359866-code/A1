# Reglas ProGuard de Omni TV — perfil profesional.
# Media3, Coil y Compose publican consumer rules; aquí solo lo específico.

-keep class com.omnitv.app.MainActivity { *; }
-keep class com.omnitv.app.data.SecretBox { *; }

# Evitar obfuscación de modelos serializados en SharedPreferences
-keep class com.omnitv.app.data.Video { *; }
-keep class com.omnitv.app.data.Profile { *; }
-keep class com.omnitv.app.data.Episode { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
