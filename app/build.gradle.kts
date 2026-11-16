plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de producción opcional. Sin secretos (build local / fork) se sigue
// usando la keystore debug para sideload, pero el release ya no está
// *obligado* a publicarse con una clave de depuración.
val releaseKeystoreFile = System.getenv("OMNITV_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
val releaseStorePassword = System.getenv("OMNITV_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("OMNITV_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = (System.getenv("OMNITV_KEY_PASSWORD") ?: releaseStorePassword)?.takeIf { it.isNotBlank() }
val hasReleaseSigning = releaseKeystoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.omnitv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omnitv.app"
        // Android TV: API 22+ (Media3 y Compose soportan 21, pero casi ningún televisor es 21).
        minSdk = 22
        targetSdk = 34
        // versionCode debe subir con cada publicación: Android rechaza una
        // actualización con un código menor o igual que el instalado.
        // (v1.2.0 se publicó con el código 3.)
        versionCode = 5
        versionName = "1.3.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = requireNotNull(releaseKeystoreFile)
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            // Con secretos OMNITV_KEYSTORE_* usa la clave de producción; si no,
            // conserva debug para sideload (no apto para Play Store).
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM gestiona versiones de ui/foundation/animation…)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)

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
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // Imágenes
    implementation(libs.coil.compose)

    // Código QR del panel de vinculación (Ajustes → Vincular)
    implementation(libs.zxing.core)

    // Extractores Kotlin y scraping usan AddonHttp (sin Jsoup/OkHttp propios).
    // Cronet envía TLS fingerprint de Chrome (JA3) para los hosts de imágenes
    // que bloquean OkHttp; se registra en el ImageLoader desde MainActivity.
    implementation(libs.cronet.embedded)

    // Tests unitarios (JVM): política de red, panel de vinculación y parsers.
    // org.json no existe en la JVM de escritorio (en Android lo pone el sistema).
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
