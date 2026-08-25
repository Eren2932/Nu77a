package club.nuva.app.di

import android.content.Context
import club.nuva.app.data.local.ServerStore
import club.nuva.app.data.local.SessionStore
import club.nuva.app.data.remote.NuvaApi
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.data.repository.AuthRepository
import club.nuva.app.data.repository.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Hand-rolled dependency container.
 *
 * No Hilt/Dagger on purpose: one annotation processor less means one class of
 * build failure less, and this app has exactly eight singletons. If the graph
 * ever grows past ~15 objects, this is the single file to replace.
 *
 * initialize() is called once from NuvaApplication.onCreate().
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var applicationScope: CoroutineScope
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var serverStore: ServerStore
        private set
    lateinit var api: NuvaApi
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var realtime: RealtimeClient
        private set
    lateinit var serverRepository: ServerRepository
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            sessionStore = SessionStore(context.applicationContext)
            serverStore = ServerStore(context.applicationContext)
            api = NuvaApi(sessionStore, serverStore)
            authRepository = AuthRepository(api, sessionStore)
            realtime = RealtimeClient(api, sessionStore, applicationScope)
            serverRepository = ServerRepository(serverStore, sessionStore, api, realtime)

            initialized = true
        }
    }
}
