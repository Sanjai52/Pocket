package `in`.marxen.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            PocketTheme(darkTheme = false) {
                PocketNavHost(appContainer = appContainer)
            }
        }
    }
}
