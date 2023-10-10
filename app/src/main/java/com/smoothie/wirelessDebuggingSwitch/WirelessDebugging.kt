package com.smoothie.wirelessDebuggingSwitch

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager

object WirelessDebugging {

    private const val TAG = "WirelessDebuggingFeature"

    fun isEnabled(context: Context): Boolean {
        val command = "settings get --user current global adb_wifi_enabled"

        val result = Utilities.executeShellCommand(context, command)
            ?: return false

        return result.isSuccess && result.out.joinToString("\n").trim().toInt() == 1
    }

    fun setEnabled(context: Context, value: Boolean) {
        val state = if (value) 1 else 0
        val command = "settings put --user current global adb_wifi_enabled $state"
        Utilities.executeShellCommand(context, command)
    }

    fun getPort(context: Context): String {
        val command = "getprop service.adb.tls.port"
        val result = Utilities.executeShellCommand(context, command)

        if (result == null || !result.isSuccess)
            throw Exception("Failed to obtain wireless debugging port!")

        return result.out.joinToString("\n")
    }

    fun getAddress(context: Context): String {
        val wm =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wm.connectionInfo
        val ipAddress = connectionInfo.ipAddress
        return Formatter.formatIpAddress(ipAddress)
    }

    fun getConnectionData(context: Context): String =
        "${getAddress(context)}:${getPort(context)}"

    /**
     * Synchronize connection data if device is connected via Wireless ADB and
     * synchronization is enabled in app's settings.
     *
     * @param context context used to create a WIFI_SERVICE
     */
    fun syncConnectionData(context: Context) {
        val connectionInfo: String
        try {
            connectionInfo = getConnectionData(context)
        }
        catch (exception: Exception) {
            Log.e(TAG, "Unable to get connection address and port.")
            exception.printStackTrace()
            return
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        var preferenceKey = context.getString(R.string.key_enable_kde_connect)
        val kdeIntegrationEnabled = preferences.getBoolean(preferenceKey, true)

        if (!KdeConnect.isInstalled(context) || !kdeIntegrationEnabled)
            return

        preferenceKey = context.getString(R.string.key_prefix_connection_data)
        val prefixConnectionData = preferences.getBoolean(preferenceKey, true)

        val connectionData =
            if (prefixConnectionData)
                KdeConnect.CLIPBOARD_PREFIX + connectionInfo
            else
                connectionInfo

        val result = KdeConnect.sendClipboard(context, connectionData)
            ?: return

        if (!result.isSuccess) {
            val message = context.getString(R.string.message_failed_sending_clipboard)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.w(TAG, result.toString())
        }
    }

}
