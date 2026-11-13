package com.omnitv.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.SettingsStore

/**
 * Actividad única de Omni TV (solo Android TV / Google TV).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La TV no se duerme mientras se navega o se reproduce.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Carga perfiles y ajustes (persistidos en SharedPreferences).
        ProfileStore.init(this)
        SettingsStore.init(this)

        setContent {
            OmniTvApp()
        }
    }
}
