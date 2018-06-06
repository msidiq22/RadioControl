package com.nikhilparanjape.radiocontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager

import com.nikhilparanjape.radiocontrol.rootUtils.Utilities

class WakeupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val util = Utilities()
        util.scheduleAlarm(context)

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = pref.edit()
        editor.putBoolean("isNoDisturbEnabled", false)
        editor.apply()
    }

    companion object {

        val REQUEST_CODE = 12345
        val ACTION = "com.nikhilparanjape.radiocontrol.services.BackgroundAirplaneService"
    }
}
