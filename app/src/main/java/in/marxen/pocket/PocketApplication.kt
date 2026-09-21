package `in`.marxen.pocket

import android.app.Application
import `in`.marxen.pocket.data.local.AppContainer

class PocketApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
