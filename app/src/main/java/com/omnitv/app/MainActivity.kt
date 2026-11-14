package com.omnitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil.Coil
import coil.ImageLoader
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore
import com.omnitv.app.network.cronetSupport

/** Actividad única de Omni TV (solo Android TV / Google TV). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileStore.init(this)
        SettingsStore.init(this)
        runCatching { com.omnitv.app.network.CronetClient.init(this) }
        // Un único ImageLoader para toda la app: registra el fetcher Cronet
        // (antes definido pero nunca instalado) y mantiene las cachés de Coil.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .cronetSupport(this)
                .crossfade(true)
                .build()
        )
        // No mantenemos la TV encendida mientras se navega. Solo el vídeo
        // activo activa keepScreenOn en PlayerScreen.
        setContent { OmniTvApp() }
    }

    override fun onStart() {
        super.onStart()
        PairingServer.start()
    }

    override fun onStop() {
        // El puerto de configuración no permanece abierto al volver al launcher.
        PairingServer.stop()
        super.onStop()
    }
}
