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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
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

    // Compose for TV (Material para televisores: Card con foco, MaterialTheme…)
    implementation(libs.androidx.tv.material)

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

    // Tests unitarios (JVM): política de red, panel de vinculación y parsers.
    // org.json no existe en la JVM de escritorio (en Android lo pone el sistema).
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
