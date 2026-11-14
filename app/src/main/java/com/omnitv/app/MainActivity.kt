package com.omnitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omnitv.app.data.PairingServer
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore

/** Actividad única de Omni TV (solo Android TV / Google TV). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileStore.init(this)
        SettingsStore.init(this)
        runCatching { com.omnitv.app.network.CronetClient.init(this) }
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
