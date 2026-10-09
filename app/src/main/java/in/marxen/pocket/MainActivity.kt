package `in`.marxen.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import `in`.marxen.pocket.data.local.AppContainer
import `in`.marxen.pocket.navigation.PocketNavHost
import `in`.marxen.pocket.ui.theme.PocketTheme

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContainer = (application as PocketApplication).container

        setContent {
            val theme by appContainer.prefs.theme.collectAsState(initial = "light")
            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            PocketTheme(darkTheme = darkTheme) {
                PocketNavHost(appContainer = appContainer)
            }
        }
    }
}
