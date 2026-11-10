// SPDX-License-Identifier: GPL-3.0-or-later
package mx.loperstack.tokkiphone.tokkip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mx.loperstack.tokkiphone.TokkipPhoneApplication
import org.json.JSONObject

/**
 * Tokkip shared between the Tokkip app on this phone and Tokkip PC: the
 * Tokkip app hands its Tokkip here ([TokkipReceiver]), the core sends it to
 * paired PCs in this phone's description, and a PC's Tokkip goes back to the
 * Tokkip app ([fromPc]). The JSON is the Tokkip apps' (docs/protocol/tokkip.md);
 * here it is only checked and carried.
 */
object TokkipLink {
    const val TOKKIP = "mx.loperstack.tokkip"
    /** From the Tokkip app: its Tokkip. */
    const val ACTION_FROM_TOKKIP = "mx.loperstack.tokkiphone.action.TOKKIP"
    /** To the Tokkip app: a PC's. */
    const val ACTION_TO_TOKKIP = "mx.loperstack.tokkip.action.PC_TOKKIP"
    const val EXTRA_JSON = "json"
    private const val MAX_LEN = 4096
    private const val PREFS = "tokkip"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A Tokkip JSON of the right size and shape. */
    fun isTokkip(json: String?): Boolean {
        if (json.isNullOrEmpty() || json.length > MAX_LEN) return false
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return false
        return o.optInt("v") == 1 && o.has("changedAt") && o.optJSONObject("look") != null
    }

    /** This phone's Tokkip, as the Tokkip app last told us. */
    fun mine(context: Context): String? = prefs(context).getString("mio", null)?.takeIf(::isTokkip)

    fun saveMine(context: Context, json: String) {
        prefs(context).edit().putString("mio", json).apply()
    }

    /** A PC's Tokkip: on to the Tokkip app, once per change. */
    fun fromPc(context: Context, json: String?) {
        if (!isTokkip(json)) return
        val p = prefs(context)
        if (p.getString("pc", null) == json) return
        val sent = runCatching {
            context.sendBroadcast(
                Intent(ACTION_TO_TOKKIP).setPackage(TOKKIP).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES).putExtra(EXTRA_JSON, json),
            )
        }.isSuccess
        if (sent) p.edit().putString("pc", json).apply()
    }
}

/** The Tokkip app hands over its Tokkip. Only a look, an outfit and today's feeling come in. */
class TokkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TokkipLink.ACTION_FROM_TOKKIP) return
        val json = intent.getStringExtra(TokkipLink.EXTRA_JSON)?.takeIf(TokkipLink::isTokkip) ?: return
        TokkipLink.saveMine(context, json)
        (context.applicationContext as? TokkipPhoneApplication)?.core?.updateTokkip(json)
    }
}
