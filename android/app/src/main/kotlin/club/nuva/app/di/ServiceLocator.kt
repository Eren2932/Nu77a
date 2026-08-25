package club.nuva.app.di

import android.content.Context
import club.nuva.app.data.local.ChatDatabase
import club.nuva.app.data.local.ServerStore
import club.nuva.app.data.local.SessionStore
import club.nuva.app.data.local.UiPrefs
import club.nuva.app.data.remote.NuvaApi
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.data.repository.AuthRepository
import club.nuva.app.data.repository.ServerRepository
import club.nuva.app.ui.chat.ChatDraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container.
 *
 * No Hilt/Dagger on purpose: one annotation processor less means one class of
 * build failure less, and this app has a handful of singletons. If the graph
 * ever grows past ~15 objects, this is the single file to replace.
 *
 * initialize() is called once from NuvaApplication.onCreate().
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    /** Lets Compose previews and unit tests avoid touching lateinit fields. */
    val isInitialized: Boolean get() = initialized

    lateinit var applicationScope: CoroutineScope
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var serverStore: ServerStore
        private set
    lateinit var uiPrefs: UiPrefs
        private set
    lateinit var api: NuvaApi
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var realtime: RealtimeClient
        private set
    lateinit var serverRepository: ServerRepository
        private set

    lateinit var chatDatabase: ChatDatabase
        private set

    /**
     * Conversations and local contacts, persisted in SQLite. No longer a
     * placeholder: as of 3.1 this survives a restart and invents nothing.
     * Renamed to `chatStore` in 3.2 with the screen rewrite.
     */
    lateinit var chatDrafts: ChatDraftStore
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            sessionStore = SessionStore(context.applicationContext)
            serverStore = ServerStore(context.applicationContext)
            uiPrefs = UiPrefs(context.applicationContext)
            api = NuvaApi(sessionStore, serverStore)
            authRepository = AuthRepository(api, sessionStore)
            realtime = RealtimeClient(api, sessionStore, applicationScope)
            serverRepository = ServerRepository(serverStore, sessionStore, api, realtime)
            chatDatabase = ChatDatabase(context.applicationContext)
            chatDrafts = ChatDraftStore(chatDatabase, applicationScope)

            initialized = true
        }
    }
}
