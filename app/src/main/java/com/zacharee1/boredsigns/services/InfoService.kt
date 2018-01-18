package com.zacharee1.boredsigns.services

import android.app.Notification
import android.content.*
import android.preference.PreferenceManager
import com.zacharee1.boredsigns.widgets.InfoWidget
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.util.Log


class InfoService : NotificationListenerService() {
    companion object {
        var NOTIFS: Array<StatusBarNotification?>? = null
        var RANKING: RankingMap? = null

        val NOTIF_BASE = "com.zacharee1.boredsigns.action."
        val NOTIF_UPDATE = NOTIF_BASE + "NOTIF_UPDATE"

        val NOTIF_LIST = "notifs"
        val RANKING_LIST = "ranks"

        var KEYS = mutableListOf(
                "show_percent",
                "battery_color",
                "am_pm",
                "24_hour",
                "clock_color",
                "show_date",
                "show_battery",
                "show_clock",
                "show_mobile",
                "mobile_color",
                "show_wifi",
                "wifi_color",
                "show_notifs"
        )

        var INTENTS = mutableListOf(
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.RSSI_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION,
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION,
                NOTIF_UPDATE
        )
    }

    private var mConnected = false

    private var mPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = SharedPreferences.OnSharedPreferenceChangeListener {
        _, s ->

        if (KEYS.contains(s) && shouldAct()) {
            sendUpdateBroadcast(null)
        }
    }

    private var mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            if (INTENTS.contains(p1?.action) && shouldAct()) {
                sendUpdateBroadcast(p1?.extras)
            }
        }
    }

    private var mTelephonyListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            if (shouldAct()) sendUpdateBroadcast(null)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (shouldAct()) sendUpdateBroadcast(null)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        if (shouldAct()) sendUpdateBroadcast(null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (shouldAct()) sendUpdateBroadcast(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(1337, Notification())

        if (!shouldAct()) {
            stopForeground(true)
            stopSelf()
            stopSelf(1337)
        }

        val filter = IntentFilter()
        for (s in INTENTS) {
            filter.addAction(s)
        }

        registerReceiver(mReceiver, filter)
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mPrefsListener)

        (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).listen(mTelephonyListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        if (shouldAct()) sendUpdateBroadcast(null)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        mConnected = true

        if (shouldAct()) {
            RANKING = currentRanking
            NOTIFS = activeNotifications

            val extras = Bundle()
            extras.putBoolean(RANKING_LIST, true)
            extras.putBoolean(NOTIF_LIST, true)

            sendUpdateBroadcast(extras)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        mConnected = false
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(mReceiver)
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(mPrefsListener)
    }

    private fun shouldAct(): Boolean {
        val man = AppWidgetManager.getInstance(this)
        val ids = man.getAppWidgetIds(ComponentName(this, InfoWidget::class.java))
        return ids != null && ids.isNotEmpty()
    }

    private fun sendUpdateBroadcast(extras: Bundle?) {
        if (mConnected) {
            RANKING = currentRanking
            NOTIFS = activeNotifications
        }

        val man = AppWidgetManager.getInstance(this)
        val ids = man.getAppWidgetIds(
                ComponentName(this, InfoWidget::class.java))
        val updateIntent = Intent()
        updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        updateIntent.putExtra("appWidgetIds", ids)
        if (mConnected) updateIntent.putExtra(RANKING_LIST, true)
        if (mConnected) updateIntent.putExtra(NOTIF_LIST, true)
        if (extras != null) updateIntent.putExtras(extras)
        updateIntent.component = ComponentName(this, InfoWidget::class.java)
        sendBroadcast(updateIntent)
    }
}