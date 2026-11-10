// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import mx.loperstack.tokkiphone.banking.BankingPause
import mx.loperstack.tokkiphone.core.Core
import mx.loperstack.tokkiphone.elevated.Elevated
import mx.loperstack.tokkiphone.ui.Preferences
import mx.loperstack.tokkiphone.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Starts the core with the process and keeps it for the app's lifetime. */
class TokkipPhoneApplication : Application(), DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val core: Core by lazy { Core(this, scope) }

    /** Updates from GitHub releases (release builds only). */
    val updater: AppUpdater by lazy { AppUpdater(this) }

    private var pendingStopJob: Job? = null

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Preferences.BACKGROUND_SERVICE_ENABLED) {
                if (Preferences.isBackgroundServiceEnabled(this)) {
                    pendingStopJob?.cancel()
                    pendingStopJob = null
                } else {
                    if (!isInForeground()) {
                        scheduleStop()
                    }
                }
            }
        }

    override fun onCreate() {
        super<Application>.onCreate()
        // Before the core starts: its network code reads the phone's DNS servers.
        mx.loperstack.tokkiphone.core.AndroidContext.install(this)
        // Before Elevated: it waits while paused for a banking app.
        BankingPause.init(this)
        BankingPause.restoreIfStranded(this)
        Elevated.init(this)
        // What the phone offers PCs follows Elevated starting and stopping.
        Elevated.onChange = { core.refreshNotificationAccess() }
        Elevated.onClipboardChanged = { text, html -> core.onElevatedClipboardChanged(text, html) }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // Received files moved from Download/Tokkip Phone to Download/Tokkip Phone (once).
        scope.launch(Dispatchers.IO) { mx.loperstack.tokkiphone.files.ReceivedFiles.moveOldFolder(this@TokkipPhoneApplication) }
        getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefListener)

        if (Preferences.isBackgroundServiceEnabled(this) && !mx.loperstack.tokkiphone.service.Rest.resting(this)) {
            android.view.Choreographer.getInstance().postFrameCallback {
                scope.launch { core.start() }
            }
            scope.launch {
                delay(120)
                core.start()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        pendingStopJob?.cancel()
        pendingStopJob = null
        // Opening the app wakes it from resting ("Salir" or no PC around).
        if (!mx.loperstack.tokkiphone.service.Rest.wake(this)) core.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!Preferences.isBackgroundServiceEnabled(this) || mx.loperstack.tokkiphone.service.Rest.resting(this)) {
            scheduleStop()
        }
    }

    fun onBackgroundServiceDisabled() {
        if (!isInForeground()) {
            scheduleStop()
        }
    }

    private fun isInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun isStopEligible(): Boolean =
        !isInForeground() && (!Preferences.isBackgroundServiceEnabled(this) || mx.loperstack.tokkiphone.service.Rest.resting(this))

    private fun scheduleStop() {
        pendingStopJob?.cancel()
        pendingStopJob = scope.launch(Dispatchers.IO) {
            delay(STOP_DELAY_MS)
            if (!isStopEligible()) return@launch

            while (core.busy.value) {
                core.busy.first { !it }
                delay(STOP_IDLE_DEBOUNCE_MS)
                if (!isStopEligible()) return@launch
            }

            if (isStopEligible()) {
                core.stop()
            }
        }
    }

    companion object {
        const val STOP_DELAY_MS = 5_000L
        const val STOP_IDLE_DEBOUNCE_MS = 2_000L
    }
}
