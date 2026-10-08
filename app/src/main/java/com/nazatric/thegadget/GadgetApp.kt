package com.nazatric.thegadget

import android.app.Application
import android.os.Build
import com.nazatric.thegadget.data.artwork.ArtworkStore
import com.nazatric.thegadget.data.db.GadgetDatabase
import com.nazatric.thegadget.data.games.GameRepository
import com.nazatric.thegadget.data.library.LibraryIndexer
import com.nazatric.thegadget.data.prefs.GadgetPrefs
import com.nazatric.thegadget.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GadgetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (isGameProcess()) return
        container = AppContainer(this)
        container.scope.launch {
            container.prefs.ensureFirstLaunch()
            container.games.refresh()
        }
    }

    private fun isGameProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) getProcessName() else ""
        return name.endsWith(":games")
    }
}

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = GadgetDatabase.get(app)
    val prefs = GadgetPrefs(app)
    val artwork = ArtworkStore(app)
    val indexer = LibraryIndexer(app, db, artwork, prefs)
    val games = GameRepository(app, db)
    val player = PlayerController(app, prefs, artwork)
}
