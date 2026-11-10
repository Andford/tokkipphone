// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.service

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import mx.loperstack.tokkiphone.TokkipPhoneApplication

/**
 * Tokkip Phone resting: after "Salir", or by itself when no PC has been
 * around for a while (the PC is off, you left the office). The connection,
 * its notification and the core stop, but nothing is turned off for good:
 * it wakes when you open the app, or when Android sees a paired PC's
 * Bluetooth beacon nearby ([mx.loperstack.tokkiphone.companion.PcCompanionService]).
 * "Correr en segundo plano" off is still the way to turn it off for good.
 */
object Rest {
    private const val PREFS = "descanso"
    private const val RESTING = "descansando"
    private const val AUTO = "automatico"

    /** No PC online for this long, with the app closed: rest. */
    const val AUTO_MS = 30 * 60_000L

    private val _left = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** "Salir" was pressed: the open screens close themselves. */
    val left: SharedFlow<Unit> = _left

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun resting(context: Context): Boolean = prefs(context).getBoolean(RESTING, false)

    /** Rest by itself when no PC is around (on unless you turn it off in Ajustes). */
    fun autoEnabled(context: Context): Boolean = prefs(context).getBoolean(AUTO, true)

    fun setAuto(context: Context, on: Boolean) = prefs(context).edit().putBoolean(AUTO, on).apply()

    /** "Salir": everything stops and the app closes. */
    fun leave(context: Context) {
        sleep(context)
        _left.tryEmit(Unit)
    }

    /** Stops the connection and the core until something wakes it. */
    fun sleep(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean(RESTING, true).commit()
        ConnectionService.stop(app)
        (app as? TokkipPhoneApplication)?.core?.stop()
    }

    /** Back to work (the app opened, a PC's beacon appeared). False if it wasn't resting. */
    fun wake(context: Context): Boolean {
        val app = context.applicationContext
        if (!resting(app)) return false
        prefs(app).edit().putBoolean(RESTING, false).commit()
        (app as? TokkipPhoneApplication)?.core?.start()
        ConnectionService.start(app)
        return true
    }
}
