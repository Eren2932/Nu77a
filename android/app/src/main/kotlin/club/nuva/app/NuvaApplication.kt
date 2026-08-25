package club.nuva.app

import android.app.Application
import android.util.Log
import club.nuva.app.di.ServiceLocator

class NuvaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Any crash before this line is a configuration bug, not a user bug:
        // log it loudly instead of dying silently in a release build.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        ServiceLocator.initialize(this)
        Log.i(TAG, "Nuva ${BuildConfig.VERSION_NAME} started, api=${BuildConfig.API_BASE_URL}")
    }

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    private companion object {
        const val TAG = "NuvaApp"
    }
}
