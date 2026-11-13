# Reglas de ProGuard de Omni TV.
# Media3, Coil y Compose ya publican sus propias reglas consumer; aquí solo
# protegemos lo específico de la app.

# Mantener la actividad principal (aunque se referencia desde el manifest).
-keep class com.omnitv.app.MainActivity { *; }
