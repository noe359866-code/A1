plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.omnitv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omnitv.app"
        // Android TV: API 22+ (Media3 y Compose soportan 21, pero casi ningún televisor es 21).
        minSdk = 22
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        getByName("debug")
        create("release") {
            // Release firmado con keystore de debug para distribución por GitHub Releases
            // (sideload en TV). Para Play Store, definir OMNITV_STORE_* en secrets.
            val storeFileEnv = System.getenv("OMNITV_STORE_FILE")
            if (storeFileEnv != null) {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("OMNITV_STORE_PASSWORD")
                keyAlias = System.getenv("OMNITV_KEY_ALIAS")
                keyPassword = System.getenv("OMNITV_KEY_PASSWORD")
            } else {
                // Fallback: reutiliza debug keystore (generado automáticamente por AGP)
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                // Si no existe aún en CI/local limpio, AGP lo crea al compilar debug;
                // usamos el del proyecto como respaldo
                if (!storeFile!!.exists()) {
                    // keystore de respaldo incluido en el repo sería ideal, pero
                    // referenciamos el debug por defecto; Gradle lo generará
                    storeFile = file("${projectDir}/../.tmp-debug.keystore")
                }
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Sin keystore de producción configurado, firma con debug para poder publicar APK en Releases
            signingConfig = signingConfigs.getByName(if (System.getenv("OMNITV_STORE_FILE") != null) "release" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM gestiona versiones de ui/foundation/animation…)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)

    // Componentes de texto (Material 2) y tema (Material 3), versiones del BOM.
    // No se usa androidx.tv:tv-material: su Material 2 para TV no expone Text
    // ni MaterialTheme en la versión 1.0.0, y los componentes con foco están
    // dibujados a mano (PosterCard, PillButton…) para no depender de APIs
    // experimentales de TV Material 3.
    implementation(libs.compose.material)
    implementation(libs.compose.material3)

    // Navegación
    implementation(libs.androidx.navigation.compose)

    // Media3 · ExoPlayer
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Imágenes
    implementation(libs.coil.compose)

    // Código QR del panel de vinculación (Ajustes → Vincular)
    implementation(libs.zxing.core)

    // Plugins Streamflix-compat: scraping + extractores (providers/*.js → native)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    // Streamflix 403 fix: Cronet envía TLS fingerprint de Chrome (JA3) en lugar de OkHttp, evita bloqueos 403 en imágenes/streams
    implementation(libs.cronet.embedded)

    // Tests unitarios (JVM): política de red, panel de vinculación y parsers.
    // org.json no existe en la JVM de escritorio (en Android lo pone el sistema).
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
