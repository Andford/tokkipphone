// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import mx.loperstack.tokkiphone.TokkipPhoneApplication
import mx.loperstack.tokkiphone.service.ConnectionService

/**
 * Bound by Android's CompanionDeviceManager when an associated PC's BLE
 * proximity beacon (`NCTR\x01` + 8-byte device ID prefix) appears nearby.
 * Wakes the foreground [ConnectionService] and triggers an immediate LAN/BLE
 * reconnect in [mx.loperstack.tokkiphone.core.Core].
 */
@RequiresApi(Build.VERSION_CODES.S)
class PcCompanionService : CompanionDeviceService() {

    @Deprecated("Deprecated in API 36 in favor of onDevicePresenceEvent")
    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        wakeAndReconnect()
    }

    @Deprecated("Deprecated in API 33")
    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        super.onDeviceAppeared(address)
        wakeAndReconnect()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)
        if (event.event == DevicePresenceEvent.EVENT_BLE_APPEARED ||
            event.event == DevicePresenceEvent.EVENT_BT_CONNECTED
        ) {
            wakeAndReconnect()
        }
    }

    private fun wakeAndReconnect() {
        val appContext = applicationContext ?: return
        if (!mx.loperstack.tokkiphone.ui.Preferences.isBackgroundServiceEnabled(appContext)) {
            return
        }
        // Your PC is near: back from resting, if it was.
        mx.loperstack.tokkiphone.service.Rest.wake(appContext)
        ConnectionService.start(appContext)
        (appContext as? TokkipPhoneApplication)?.core?.reconnectNow()
    }
}
