package com.nikhilparanjape.radiocontrol.services

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.text.format.DateFormat
import android.util.Log
import android.util.Log.DEBUG
import android.util.Log.INFO
import com.nikhilparanjape.radiocontrol.receivers.ConnectivityReceiver
import com.nikhilparanjape.radiocontrol.utilities.AlarmSchedulers
import com.nikhilparanjape.radiocontrol.utilities.Utilities
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.getCellStatus
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.getCurrentSsid
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.isAirplaneMode
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.isCallActive
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.isConnectedMobile
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.isConnectedWifi
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.setMobileNetworkFromLollipop
import com.nikhilparanjape.radiocontrol.utilities.Utilities.Companion.writeLog
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.net.InetAddress

/**
 * This service starts the BackgroundJobService as a foreground service if on Android Oreo or higher.
 *
 * This is the brains of the app
 *
 * @author Nikhil Paranjape
 *
 */
@SuppressLint("SpecifyJobSchedulerIdRange")
class BackgroundJobService : JobService(), ConnectivityReceiver.ConnectivityReceiverListener {

    //internal var util = Utilities() //Network and other related utilities
    private var alarmUtil = AlarmSchedulers()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BackgroundJobScheduler created")
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "Job started")
        writeLog(TAG, "Job Started", applicationContext, INFO)
        //Utilities.scheduleJob(applicationContext) // reschedule the job

        //val sp = applicationContext.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext) //General Shared Prefs for the whole app
        val disabledPref = applicationContext.getSharedPreferences("disabled-networks", Context.MODE_PRIVATE) //Preferences for the Network Blocklist

        //TODO Transfer to external algorithm class
        //val h = HashSet(listOf("")) //Set default empty set for SSID check
        val selections = prefs.getStringSet("ssid", HashSet(listOf(""))) //Gets stringset, if empty sets default
        val networkAlert = prefs.getBoolean("isNetworkAlive", false) //Value for if user wants network alerts

        if (isOngoingPhoneCall){
            enableNetworks(prefs, params, applicationContext)
        }
        //Check if user wants the app on
        if (prefs.getInt("isActive", 0) == 0) { //This means they DO NOT want the app enabled, or the app is disabled
            Log.d(TAG, "RadioControl has been disabled")
            //If the user wants to be alerted when the network is not internet reachable
            if (networkAlert) { //If the user wants network alerts only
                pingTask() //Run a network latency check
            }
            //Adds wifi signal lost log for devices that aren't rooted
            if (!isConnectedWifi(applicationContext)) {
                Log.d(TAG, WIFI_LOST)
                writeLog(WIFI_LOST, applicationContext)
            }
            jobFinished(params, false) //Tell android that this job is finished and can be closed out
        } else if (prefs.getInt("isActive", 0) == 1) { //This means the user DOES want the app enabled
            Log.d(TAG, "Main Program Begin")
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager //Initializes the Connectivity Manager. This should only be done if the user requested the app to be active
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), NetworkCallbackRequest) //Registers for network callback notifications - TODO Bug reported ConnectivityManager$TooManyRequestsException
            val activeNetwork = connectivityManager.activeNetworkInfo //This is used to check if the mobile network is currently off/disabled
            /**^TODO refactor activeNetworkInfo to NetworkCallback API
             *  ConnectivityManager.NetworkCallback API
                or ConnectivityManager#getNetworkCapabilities or ConnectivityManager#getLinkProperties

                https://stackoverflow.com/questions/53532406/activenetworkinfo-type-is-deprecated-in-api-level-28
             **/
            Log.d(TAG, "Status?: $activeNetwork") //Gives a run down of the current network

            /** Section for on network losing status
             * connectivityManager.requestNetwork(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {}, 5000)
             * **/
            //Check if there is no WiFi connection && the Cell network is still not active
            if (!isConnectedWifi(applicationContext) && activeNetwork == null) {
                Log.d(TAG, WIFI_LOST)
                writeLog(WIFI_LOST, applicationContext)

                // Ensures that Airplane mode is on, or that the cell radio is off
                if (isAirplaneMode(applicationContext) || !isConnectedMobile(applicationContext)) {

                    //Runs the alt cellular mode, otherwise, run the standard airplane mode
                    if (prefs.getBoolean("altRootCommand", false)) {
                        if (getCellStatus(applicationContext) == 1) {
                            val output = Shell.cmd("service call phone 27").exec()
                            writeLog("root accessed: $output", applicationContext)
                            Log.d(TAG, CELL_RADIO_ON)
                            writeLog(CELL_RADIO_ON, applicationContext)
                            jobFinished(params, false)
                        }
                    } else {
                        val output = Shell.cmd("settings put global airplane_mode_on 0", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false").exec()
                        writeLog("root accessed: $output", applicationContext)
                        //RootAccess.runCommands(airOffCmd3) *Here for legacy purposes
                        Log.d(TAG, AIRPLANE_MODE_OFF)
                        writeLog(AIRPLANE_MODE_OFF, applicationContext)
                        jobFinished(params, false)
                    }
                }
            //Here we check if the device just connected to a wifi network, as well as if airplane mode and/or the cell radio are off and/or connected respectively
            } else if (isConnectedWifi(applicationContext) && !isAirplaneMode(applicationContext)) {
                Log.d(TAG, "WiFi signal got")

                //Checks if the currently connected SSID is in the list of disabled networks
                if (!disabledPref.contains(getCurrentSsid(applicationContext))) {
                    Log.d(TAG, "The current SSID was not found in the disabled list")

                    //Checks that user is not in call
                    if (!isCallActive(applicationContext)) {

                        //Checks if the user doesn't want network alerts
                        if (!networkAlert) {

                            //Runs the cellular mode, otherwise, run default airplane mode
                            if (prefs.getBoolean("altRootCommand", false)) {

                                when {
                                    getCellStatus(applicationContext) == 0 -> {
                                        val output = Shell.cmd("service call phone 27").exec()
                                        writeLog("root accessed: $output", applicationContext)
                                        Log.d(TAG, CELL_RADIO_OFF)
                                        writeLog(CELL_RADIO_OFF, applicationContext)
                                        jobFinished(params, false)
                                    }
                                    getCellStatus(applicationContext) == 1 -> {
                                        Log.d(TAG, "Cell Radio is already off")
                                        jobFinished(params, false)
                                    }
                                    getCellStatus(applicationContext) == 2 -> {
                                        Log.e(TAG, "Location can't be accessed, try alt method")
                                        setMobileNetworkFromLollipop(applicationContext)
                                    }
                                }

                            } else {
                                val output = Shell.cmd("settings put global airplane_mode_radios  \"cell\"", "content update --uri content://settings/global --bind value:s:'cell' --where \"name='airplane_mode_radios'\"", "settings put global airplane_mode_on 1", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true").exec()
                                writeLog("root accessed: $output", applicationContext)
                                //RootAccess.runCommands(airCmd)
                                Log.d(TAG, AIRPLANE_MODE_ON)
                                writeLog(AIRPLANE_MODE_ON, applicationContext)
                                jobFinished(params, false)
                            }
                        } else {
                            pingTask()
                            jobFinished(params, false)
                        }//The user does want network alert notifications

                    }//Checks that user is currently in call and pauses execution till the call ends
                    //Waits for the active call to finish before initiating
                    else if (isCallActive(applicationContext)) {
                        //Here we want to wait for the phone call to end before closing off the cellular connection. Don't assume WiFi handoff
                        Log.d(TAG, "Still on the phone")
                        jobFinished(params, true)
                        /*while (isCallActive(applicationContext)) {
                            waitFor(2000)//Wait for call to end. TODO this is actually dangerous and should be changed as it hangs the whole app
                            Log.d(TAG, "waiting for call to end")
                        }*/
                        /*[Legacy Code] Waits for the active call to end, however, now it will just do a jobFinished. Idk if it works properly*/

                    }
                } else if (selections!!.contains(getCurrentSsid(applicationContext))) {
                    Log.i(TAG, "The current SSID was blocked from list $selections")
                    writeLog("The current SSID was blocked from list $selections", applicationContext)
                    jobFinished(params, false)
                }//Pauses because WiFi network is in the list of disabled SSIDs
            }
            //Handle any other event not covered above, usually something is not right, or we lack some permission, or the app is double running
            else {
                if (activeNetwork != null) {
                    Log.d(TAG, "We are connected")
                } else {
                    //So activeNetwork has to have some value, lets see what that is
                    Log.e(TAG, "EGADS: $activeNetwork")
                }
                jobFinished(params, false)
            }
            //Since the code is finished, we should run the unregisterNetworkCallback
            //This is to avoid hitting 100 request limit shared with registerNetworkCallback
            connectivityManager.unregisterNetworkCallback(NetworkCallbackRequest)
        } else {
            Log.e(TAG, "Something's wrong, I can feel it")
        }
        Log.i(TAG, "Job completed")
        jobFinished(params, false)
        return true
    }

    private fun enableNetworks(prefs: SharedPreferences, params: JobParameters, context: Context){
        // Ensures that Airplane mode is on, or that the cell radio is off
        if (isAirplaneMode(context) || !isConnectedMobile(context)) {

            //Runs the alt cellular mode, otherwise, run the standard airplane mode
            if (prefs.getBoolean("altRootCommand", false)) {
                if (getCellStatus(context) == 1) {
                    val output = Shell.cmd("service call phone 27").exec()
                    writeLog("root accessed: $output", context)
                    Log.d(TAG, CELL_RADIO_ON)
                    writeLog(CELL_RADIO_ON, context)
                    jobFinished(params, false)
                }
            } else {
                val output = Shell.cmd("settings put global airplane_mode_on 0", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false").exec()
                writeLog("root accessed: $output", context)
                //RootAccess.runCommands(airOffCmd3) *Here for legacy purposes
                Log.d(TAG, AIRPLANE_MODE_OFF)
                writeLog(AIRPLANE_MODE_OFF, context)
                jobFinished(params, false)
            }
        }
    }
    /**
     * Write a private log for the Statistics Activity
     *
     * Sets the date in yyyy-MM-dd HH:mm:ss format
     *
     * This method always requires appropriate context
     *
     * @param data The data to be written to the log file radiocontrol.log
     * @param c context allows access to application-specific resources and classes
    */
    private fun writeLog(data: String, c: Context) {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(c)
        if (preferences.getBoolean("enableLogs", false)) {
            try {
                val h = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString()
                val log = File(c.filesDir, "radiocontrol.log")
                if (!log.exists()) {
                    log.createNewFile() // Create a log file if it does not exist
                }
                val logPath = "radiocontrol.log"
                val string = "\n$h: $data"

                val fos = c.openFileOutput(logPath, Context.MODE_APPEND)
                fos.write(string.toByteArray()) // Writes the current string to a bytearray into the path radiocontrol.log
                fos.close()
            } catch (e: IOException) {
                Log.d(TAG, "There was an error saving the log: $e")
            }
        }
    }

    /**
     * Checks latency to CloudFlare
     *
     * This method always requires application context
     *
     */
    private fun pingTask() {
        try {
            //Wait for network to be connected fully
            /*while (!isConnected(applicationContext)) {
                //Thread.sleep(1000)
            }*/
            //Fix this, as it causes a hang when wifi is disconnected and the app is on airplane mode(Only if Internet Test is active)
            /*if (!isConnected(applicationContext)){
                waitFor(5000)
            }*/ //Commented out because address.isReachable has a timeout of 5 seconds
            val address = InetAddress.getByName("1.1.1.1")
            val reachable = address.isReachable(5000)
            Log.d(TAG, "Reachable?: $reachable")

            //val sp = applicationContext.getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE)
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)

            val alertPriority = prefs.getBoolean("networkPriority", false)//Setting for network notifier
            val alertSounds = prefs.getBoolean("networkSound", false)
            val alertVibrate = prefs.getBoolean("networkVibrate", false)


            if (prefs.getInt("isActive", 0) == 0) {
                //If the connection can't reach Google
                if (!reachable) {
                    Utilities.sendNote(applicationContext, applicationContext.getString(com.nikhilparanjape.radiocontrol.R.string.not_connected_alert), alertVibrate, alertSounds, alertPriority)
                    writeLog("Not connected to the internet", applicationContext)
                }
            } else if (prefs.getInt("isActive", 0) == 1) {
                //If the connection can't reach Google
                if (!reachable) {
                    Utilities.sendNote(applicationContext, applicationContext.getString(com.nikhilparanjape.radiocontrol.R.string.not_connected_alert), alertVibrate, alertSounds, alertPriority)
                    writeLog("Not connected to the internet", applicationContext)
                } else {
                    //Runs the cellular mode
                    if (prefs.getBoolean("altRootCommand", false)) {
                        val output = Shell.cmd("service call phone 27").exec()
                        Utilities.writeLog("root accessed: $output", applicationContext)
                        alarmUtil.scheduleRootAlarm(applicationContext)
                        Log.d(TAG, "Cell Radio has been turned off")
                        writeLog("Cell radio has been turned off", applicationContext)
                    } else if (!prefs.getBoolean("altRootCommand", false)) {
                        val output = Shell.cmd("settings put global airplane_mode_radios  \"cell\"", "content update --uri content://settings/global --bind value:s:'cell' --where \"name='airplane_mode_radios'\"", "settings put global airplane_mode_on 1", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true").exec()
                        Utilities.writeLog("root accessed: $output", applicationContext)
                        //RootAccess.runCommands(airCmd)
                        Log.d(TAG, "Airplane mode has been turned on")
                        writeLog("Airplane mode has been turned on", applicationContext)
                    }
                }
            }

        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun waitFor(timer: Long) { // It's complaining that the timer is always 1000. Like, ok, but what if it isn't? Didn't think if that did you
        try {
            Thread.sleep(timer)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }



    companion object {
        object NetworkCallbackRequest : ConnectivityManager.NetworkCallback()
        const val TAG = "RadioControl-JobSrv"
        /*private const val PRIVATE_PREF = "prefs"*/
        var isOngoingPhoneCall = false
        private const val WIFI_LOST = "WiFi signal LOST"
        const val CELL_RADIO_ON = "Cell Radio has been turned on"
        const val CELL_RADIO_OFF = "Cell Radio has been turned on"
        const val AIRPLANE_MODE_OFF = "Airplane mode has been turned off"
        const val AIRPLANE_MODE_ON = "Airplane mode has been turned on"


    }

}

