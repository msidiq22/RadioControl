package com.nikhilparanjape.radiocontrol.activities

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.View.inflate
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode
import com.afollestad.materialdialogs.MaterialDialog
import com.github.stephenvinouze.core.managers.KinAppManager
import com.github.stephenvinouze.core.models.KinAppProductType
import com.github.stephenvinouze.core.models.KinAppPurchase
import com.github.stephenvinouze.core.models.KinAppPurchaseResult
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mikepenz.iconics.Iconics
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.paddingDp
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.materialdrawer.iconics.withIcon
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.*
import com.mikepenz.materialdrawer.util.addItems
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import com.nikhilparanjape.radiocontrol.BuildConfig
import com.nikhilparanjape.radiocontrol.R
import com.nikhilparanjape.radiocontrol.databinding.ActivityMainBinding
import com.nikhilparanjape.radiocontrol.receivers.ActionReceiver
import com.nikhilparanjape.radiocontrol.receivers.ConnectivityReceiver
import com.nikhilparanjape.radiocontrol.services.BackgroundJobService
import com.nikhilparanjape.radiocontrol.services.CellRadioService
import com.nikhilparanjape.radiocontrol.services.PersistenceService
import com.nikhilparanjape.radiocontrol.utilities.AlarmSchedulers
import com.nikhilparanjape.radiocontrol.utilities.Utilities
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.net.InetAddress
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/**
 * Created by Nikhil Paranjape on 11/3/2015.
 *
 * Converted to Kotlin on 10/06/2018.
 */

class MainActivity : AppCompatActivity(), KinAppManager.KinAppListener, CoroutineScope {

    //Public key for donation
    private val base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxnZmUx4gqEFCsMW+/uPXIzJSaaoP4J/2RVaxYT9Be0jfga0qdGF+Vq56mzQ/LYEZgLvFelGdWwXJ5Izq5Wl/cEW8cExhQ/WDuJvYVaemuU+JnHP1zIZ2H28NtzrDH0hb59k9R8owSx7NPNITshuC4MPwwOQDgDaYk02Hgi4woSzbDtyrvwW1A1FWpftb78i8Pphr7bT14MjpNyNznk4BohLMncEVK22O1N08xrVrR66kcTgYs+EZnkRKk2uPZclsPq4KVKG8LbLcxmDdslDBnhQkSPe3ntAC8DxGhVdgJJDwulcepxWoCby1GcMZTUAC1OKCZlvGRGSwyfIqbqF2JQIDAQAB"

    private val billingManager = KinAppManager(this, base64EncodedPublicKey)

    // Overridden from CoroutineScope,
    // Main context that is combined with the context of the Job as well
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    // IO context that is combined with the context of the Job as well
    val ioContext: CoroutineContext
        get() = Dispatchers.IO + job

    //All the constant values and contexts required
    companion object {
        private const val PRIVATE_PREF = "prefs" //Used for shared prefs
        private const val VERSION_KEY = "version_number"
        //Donation related keys
        internal const val ITEM_ONE_DOLLAR = "com.nikihlparanjape.radiocontrol.donate.one"
        internal const val ITEM_THREE_DOLLAR = "com.nikihlparanjape.radiocontrol.donate.three"
        internal const val ITEM_FIVE_DOLLAR = "com.nikihlparanjape.radiocontrol.donate.five"
        internal const val ITEM_TEN_DOLLAR = "com.nikihlparanjape.radiocontrol.donate.ten"
        private var alarmUtil = AlarmSchedulers() //Allows methods to extend the AlarmScheduler Utility

        //private var gUtility = GraphicsUtility() //Implements all graphics and the like
        private lateinit var deviceIcon: Drawable
        private lateinit var carrierIcon: Drawable
        //private lateinit var headerView: AccountHeaderView //Needed for an older version of MaterialDrawer
        private var versionName = BuildConfig.VERSION_NAME //Takes the apps current version name and makes it a variable
        //private var util = Utilities() //Allows methods to extend any general utilities
        private lateinit var clayout: CoordinatorLayout
        private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
        private var mServiceComponent: ComponentName? = null
        private lateinit var binding: ActivityMainBinding
        private var isBillingReady = false

        //JobID for jobscheduler(BackgroundJobService)
        private const val jobID = 0x01
        // Job variable that is tied to the Activity lifecycle Coroutines
        lateinit var job: Job

        //Grab device make and model for drawer
        val deviceName: String
            get() {
                val manufacturer = Build.MANUFACTURER
                val model = Build.MODEL
                return if (model.startsWith(manufacturer)) {
                    capitalize(model)
                } else {
                    capitalize(manufacturer) + " " + model
                }
            }

        //Capitalizes names for devices. Used by deviceName()
        private fun capitalize(s: String?): String {
            //Send nothing if string is empty
            if (s == null || s.isEmpty()) {
                return ""
            }
            val first = s[0]
            return if (Character.isUpperCase(first)) {
                s
            } else {
                Character.toUpperCase(first) + s.substring(1)
            }
        }
    }

    //This is the main activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Bind activity to other components
        binding = ActivityMainBinding.inflate(layoutInflater)
        billingManager.bind(this)

        //Sets root view, toolbar, and the slider with the main binding
        val view = binding.root
        val toolbar = binding.toolbar
        val slider = binding.slider

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy) // Sets thread policy to all threads THIS IS DANGEROUS

        //  Pref values
        //  Initialize SharedPreferences
        val getPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val sharedPref = getSharedPreferences(PRIVATE_PREF, Context.MODE_PRIVATE) /** Migrated away from custom prefs.xml file **/
        val editor = getPrefs.edit()

        setContentView(view)
        //Sets the secondary view features

        mServiceComponent = ComponentName(this, BackgroundJobService::class.java)
        clayout = findViewById(R.id.clayout)
        val dialog = findViewById<ProgressBar>(R.id.pingProgressBar)
        Iconics.init(this)

        // Handle Toolbar
        //val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        val fab = findViewById<FloatingActionButton>(R.id.fab)

        //Adds filter for app to get Connectivity_Action
        val filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)

        //Sets the actionbar with hamburger icon, colors, and padding
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(IconicsDrawable(this, GoogleMaterial.Icon.gmd_menu).apply {
                colorInt = Color.WHITE
                sizeDp = 24
                paddingDp = 1
            })
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
            actionBarDrawerToggle = ActionBarDrawerToggle(this, view, toolbar, com.mikepenz.materialdrawer.R.string.material_drawer_open, com.mikepenz.materialdrawer.R.string.material_drawer_close)
        }

        //Creates the latency checker FAB button
        fab.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_network_check_white_48dp))
        fab.setOnClickListener {
            dialog.visibility = View.VISIBLE
            lifecycleScope.launch {
                pingCheck()
            }
        }

        //  TextViews
        val statusText = findViewById<TextView>(R.id.statusText)
        val linkText = findViewById<TextView>(R.id.linkSpeed)
        val connectionStatusText = findViewById<TextView>(R.id.pingStatus)

        /**  BEGIN buttons **/
        //  UI Switches and Buttons
        val linkSpeedButton = findViewById<Button>(R.id.linkSpeedButton)
        val toggle = findViewById<SwitchMaterial>(R.id.enableSwitch)
        //UI Switches and Buttons

        //  Dev buttons
        val conn = findViewById<Button>(R.id.pingTestButton)
        val serviceTest = findViewById<Button>(R.id.airplane_service_test)
        val nightCancel = findViewById<Button>(R.id.night_mode_cancel)
        val radioOffButton = findViewById<Button>(R.id.cellRadioOff)
        val forceCrashButton = findViewById<Button>(R.id.forceCrashButton)
        /**  END buttons **/

        /*Other info setters*/
        //  Create a new boolean and preference and set it to true if it's not already there
        val isFirstStart = getPrefs.getBoolean(getString(R.string.preference_first_start), true) //TODO Check why it does this every launch
        //Gets the current android build version on device
        val currentapiVersion = Build.VERSION.SDK_INT
        /*END info setters*/

        /** First start init **/
        programVersionUpdateInit(isFirstStart)//initializes the whats new dialog

        //  If this is the first time the user has opened the app...
        var carrierName = "Not Rooted" //For drawer's root status and assume not rooted
        editor.putInt(getString(R.string.preference_app_active), 0)
        //Checks for root, if none, disabled toggle switch
        if (!Shell.rootAccess()) {
            statusText.setText(R.string.noRoot)    //Text formatters

            statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated)) //Sets text to deactivated (RED) color
            editor.apply() //Commit new values to sharedprefs

            //Drawer icon
            carrierIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_error_outline).apply {
                colorInt = Color.RED /**    Useful now as the toggle is no longer disabled here**/
            }
            // TODO Show snackbar asking to request root (Maybe only do this when flipping switch?)
        } else { //We have root, do root related initializations
            carrierIcon = IconicsDrawable(this, GoogleMaterial.Icon.gmd_check_circle).apply {
                colorInt = Color.GREEN
            }
            carrierName = "Rooted"
        }

        //Checks if workmode(Intelligent Mode) is enabled and starts the Persistence Service, otherwise it registers the legacy broadcast receivers
        if (getPrefs.getBoolean(getString(R.string.preference_work_mode), false)) {
            val i = Intent(applicationContext, PersistenceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) { //   Check that we are running on Android Nougat+
                applicationContext.startForegroundService(i)
            } else {
                applicationContext.startService(i) //Start legacy service mode
            }
            Log.d("RadioControl-Main", "persistence service launched")
        } else {
            registerForBroadcasts(applicationContext)
        }
        //Checks if background optimization is enabled and schedules a job
        if (getPrefs.getBoolean(getString(R.string.key_preference_settings_battery_opimization), false)) {
            scheduleJob()
        }
        //Hides the progress dialog
        dialog.visibility = View.GONE

        //Begin initializing drawer

        //Sets
        deviceIcon = when {
            deviceName.contains("Nexus") -> AppCompatResources.getDrawable(applicationContext, R.mipmap.ic_nexus_logo)!!
            deviceName.contains("Pixel") -> AppCompatResources.getDrawable(applicationContext, R.drawable.ic_google__g__logo)!!
            deviceName.contains("Huawei") -> AppCompatResources.getDrawable(applicationContext, R.drawable.ic_huawei_logo)!!
            deviceName.contains("LG") -> AppCompatResources.getDrawable(applicationContext, R.drawable.ic_lg_logo_white)!!
            deviceName.contains("Motorola") -> AppCompatResources.getDrawable(applicationContext, R.mipmap.moto2)!!
            deviceName.contains("OnePlus") -> AppCompatResources.getDrawable(applicationContext, R.mipmap.oneplus)!!
            deviceName.contains("Samsung") -> AppCompatResources.getDrawable(applicationContext, R.mipmap.samsung)!!

            else -> AppCompatResources.getDrawable(applicationContext, R.mipmap.ic_launcher)!!
        }


        val profile = ProfileDrawerItem().apply { nameText = deviceName; descriptionText = "v$versionName"; iconDrawable = deviceIcon }
        val profile2 = ProfileDrawerItem().apply { nameText = getString(R.string.profile_root_status); descriptionText = carrierName; iconDrawable = carrierIcon }

        //Creates navigation drawer header
        val headerView = AccountHeaderView(this).apply {
            attachToSliderView(slider)
            addProfiles(
                    profile,
                    profile2
            )
            withSavedInstance(savedInstanceState)
        }
        //Creates navigation drawer items
        val item1 = PrimaryDrawerItem().withIdentifier(1).withName(R.string.home).withIcon(GoogleMaterial.Icon.gmd_wifi)
        val item2 = SecondaryDrawerItem().withIdentifier(2).withName(R.string.settings).withIcon(GoogleMaterial.Icon.gmd_settings).withSelectable(false)
        val item3 = SecondaryDrawerItem().withIdentifier(3).withName(R.string.about).withIcon(GoogleMaterial.Icon.gmd_info).withSelectable(false)
        val item4 = SecondaryDrawerItem().withIdentifier(4).withName(R.string.donate).withIcon(GoogleMaterial.Icon.gmd_attach_money).withSelectable(false)
        val item5 = SecondaryDrawerItem().withIdentifier(5).withName(R.string.sendFeedback).withIcon(GoogleMaterial.Icon.gmd_send).withSelectable(false)
        val item6 = SecondaryDrawerItem().withIdentifier(6).withName(R.string.stats).withIcon(GoogleMaterial.Icon.gmd_timeline).withSelectable(false)
        val item7 = SecondaryDrawerItem().withIdentifier(7).withName(R.string.standby_drawer_name).withIcon(GoogleMaterial.Icon.gmd_pause_circle_outline).withSelectable(false)
        val item8 = SecondaryDrawerItem().withIdentifier(8).withName(R.string.drawer_string_troubleshooting).withIcon(GoogleMaterial.Icon.gmd_help).withSelectable(false)
        slider.apply {
            addItems(
                    item1,
                    DividerDrawerItem(),
                    item2,
                    item6,
                    item3,
                    DividerDrawerItem(),
                    item8,
                    item4,
                    item5,
                    item7
            )
            onDrawerItemClickListener = { v, _, position ->
                Log.d("RadioControl-Main", "The drawer is at position $position")
                if (position == 3) {
                    startSettingsActivity()

                    Log.d("drawer", "Started settings activity")
                } else if (position == 4) {
                    val log = File(applicationContext.filesDir, "radiocontrol.log")
                    if (log.exists() && log.canRead()) {
                        Log.d("RadioControl-Main", "Log Exists")
                        startStatsActivity()
                    } else {
                        Snackbar.make(clayout, "No log file found", Snackbar.LENGTH_LONG)
                                .show()
                    }
                } else if (position == 5) {
                    startAboutActivity()
                    Log.d("drawer", "Started about activity")
                } else if (position == 7) {
                    Snackbar.make(clayout, "Coming in v5.1!", Snackbar.LENGTH_LONG)
                            .show()
                    startTroubleActivity()
                } else if (position == 8) {
                    //Donation
                    Log.d("RadioControl-Main", "Donation button pressed")
                    if (Utilities.isConnected(applicationContext) && isBillingReady) {
                        showDonateDialog()
                    } else {
                        showErrorDialog()
                    }
                } else if (position == 9) {
                    Log.d("RadioControl-Main", "Feedback")
                    sendFeedback()
                } else if (position == 10) {
                    Log.d("RadioControl-Main", "Standby Mode Engaged")
                    startStandbyMode()
                }
                false
            }
            setSavedInstance(savedInstanceState)
        }
        if (savedInstanceState == null) {
            // set the selection to the item with the identifier 11
            slider.setSelection(21, false)

            //set the active profile
            headerView.activeProfile = profile
        }
        //Create navigation drawer
        slider.setSelection(1)

        //Check if the easter egg(Dev mode) is NOT activated
        if (!getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            linkSpeedButton.visibility = View.GONE
            linkText.visibility = View.GONE
        } else if (getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            linkSpeedButton.visibility = View.VISIBLE
            linkText.visibility = View.VISIBLE
        }

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        //DEV View | Listener for the link speed button
        linkSpeedButton.setOnClickListener {
            //showWifiInfoDialog();
            val activeNetwork = connectivityManager.activeNetworkInfo
            val cellStat = Utilities.getCellStatus(applicationContext)
            Log.d("RadioControl-Job", "Active: $activeNetwork")
            Log.d("RadioControl-Main", "Cell: $cellStat")
            val linkSpeed = Utilities.linkSpeed(applicationContext)
            val gHz = Utilities.frequency(applicationContext)
            if (linkSpeed == -1) {
                linkText.setText(R.string.cellNetwork)
            } else {
                if (gHz == 2) {
                    linkText.text = getString(R.string.link_speed_24, linkSpeed)

                } else if (gHz == 5) {
                    linkText.text = getString(R.string.link_speed_5, linkSpeed)

                }
            }
        }
        //Kotlin - KinApp needs to update
        /*launch() {
            val list: ArrayList<String> = ArrayList()
            list.add(ITEM_ONE_DOLLAR)
            list.add(ITEM_THREE_DOLLAR)
            list.add(ITEM_FIVE_DOLLAR)
            list.add(ITEM_TEN_DOLLAR)
            billingManager.fetchProducts(list, KinAppProductType.INAPP).await()
        }*/

        //Dev mode handling
        //Check if the easter egg is NOT activated
        if (!getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            conn.visibility = View.GONE
            serviceTest.visibility = View.GONE
            nightCancel.visibility = View.GONE
            connectionStatusText.visibility = View.GONE
            radioOffButton.visibility = View.GONE
            forceCrashButton.visibility = View.GONE
        } else if (getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            conn.visibility = View.VISIBLE
            serviceTest.visibility = View.VISIBLE
            nightCancel.visibility = View.VISIBLE
            connectionStatusText.visibility = View.VISIBLE
            radioOffButton.visibility = View.VISIBLE
            forceCrashButton.visibility = View.VISIBLE
        }

        conn.setOnClickListener {
            connectionStatusText.setText(R.string.ping)
            connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.material_grey_50))
            dialog.visibility = View.VISIBLE
            lifecycleScope.async {
                pingCheck()
            }
        }
        serviceTest.setOnClickListener {
            val i = Intent(applicationContext, BackgroundJobService::class.java)
            applicationContext.startService(i)
            alarmUtil.scheduleAlarm(applicationContext)
            Log.d("RadioControl-Main", "Service started")
        }
        nightCancel.setOnClickListener {
            /*val intent = Intent(applicationContext, NightModeReceiver::class.java)
            val pIntent = PendingIntent.getBroadcast(applicationContext, NightModeReceiver.REQUEST_CODE,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val alarm = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pIntent)*/
            val app = "NightMode"
            alarmUtil.cancelAlarm(applicationContext, app, app)
        }
        radioOffButton.setOnClickListener {
            //String[] cellOffCmd = {"service call phone 27","service call phone 14 s16"};
            //Utilities.setMobileNetworkFromLollipop(applicationContext)
            //RootAccess.runCommands(cellOffCmd);

            val cellIntent = Intent(applicationContext, CellRadioService::class.java)
            startService(cellIntent)
            alarmUtil.scheduleRootAlarm(applicationContext)
        }
        /** Main Radiocontrol toggle switch  **/
        toggle.setOnCheckedChangeListener { _, isChecked ->
            val bgj = Intent(applicationContext, BackgroundJobService::class.java)

            if (!isChecked) {
                //Preference handling
                editor.putInt(getString(R.string.preference_app_active), 0)

                //UI Handling
                statusText.setText(R.string.showDisabled) //Sets status text to disabled
                statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))

                // TODO Don't check for this here, change to checking when enabling
                /*if (!Shell.rootAccess()) {
                    //toggle.isEnabled = false
                    //statusText.setText(R.string.noRoot)
                }*/

            } else {
                //Preference handling
                editor.putInt(getString(R.string.preference_app_active), 1)

                //Check for root first
                if (!Shell.rootAccess()) {
                    //toggle.isEnabled = false
                    toggle.isChecked = false //Uncheck toggle
                    statusText.setText(R.string.noRoot) //Sets the status text to no root
                }
                else{
                    //UI Handling
                    statusText.setText(R.string.showEnabled) //Sets the status text to enabled
                    statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))

                    //Service initialization
                    applicationContext.startService(bgj)
                    //Alarm scheduling
                    alarmUtil.scheduleAlarm(applicationContext)

                    //Checks if workmode is enabled and starts the Persistence Service, otherwise it registers the broadcast receivers
                    if (getPrefs.getBoolean(getString(R.string.preference_work_mode), true)) {
                        val i = Intent(applicationContext, PersistenceService::class.java)
                        if (Build.VERSION.SDK_INT >= 26) {
                            applicationContext.startForegroundService(i)
                        } else {
                            applicationContext.startService(i)
                        }
                        Log.d("RadioControl-Main", "persist Service launched")
                    } else {
                        registerForBroadcasts(applicationContext)
                    }
                    //Checks if background optimization is enabled and schedules a job
                    if (getPrefs.getBoolean(getString(R.string.key_preference_settings_battery_opimization), false)) {
                        scheduleJob()
                    }
                }



            }
            editor.apply()
        }
        toggle.setOnLongClickListener {
            val bgj = Intent(applicationContext, BackgroundJobService::class.java)

            //Preference handling
            editor.putInt(getString(R.string.preference_app_active), 1)
            //UI Handling
            statusText.setText(R.string.showEnabledDebug)
            statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated_debug))
            applicationContext.startService(bgj)
            alarmUtil.scheduleAlarm(applicationContext)
            Toast.makeText(applicationContext, "The impossible was just attempted",
                    Toast.LENGTH_LONG).show()
            editor.apply()
            false
        }


    }

    //Initialize method for the Whats new dialog
    private fun programVersionUpdateInit(isFirstStart: Boolean) {
        Log.d("RadioControl-Main","CHECKING FOR NEW VERSION")
        lifecycleScope.launch {
            val getPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val editor = getPrefs.edit()
            var currentVersionNumber: Int

            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                val longVersionCode = getLongVersionCode(pi)
                currentVersionNumber = longVersionCode.toInt()
            } catch (e: Exception) {
                currentVersionNumber = -1
                Log.e("RadioControl-Main", "Unable to get version number")
            }
            if (isFirstStart){
                Log.d("RadioControl-Main","IT'S YOUR FIRST TIME HERE")
                currentVersionNumber = 0
                editor.putInt(getString(R.string.preference_app_active), 0) //Sets app to "off" for default

                // Edit preference to make it false because we don't want this to run again
                editor.putBoolean(getString(R.string.preference_first_start), false)

                //Enables Intelligent Mode if Nougat+
                if (Build.VERSION.SDK_INT >= 24) { // && !getPrefs.getBoolean(getString(R.string.preference_work_mode), false)
                    editor.putBoolean(getString(R.string.preference_work_mode), true)
                }

                //  Launch tutorial/onboarding
                editor.apply()
                val i = Intent(applicationContext, TutorialActivity::class.java)
                startActivity(i)
            }
            val savedVersionNumber = getPrefs.getInt(VERSION_KEY, 1)
            //Sets app version number

            //Checks if app version has changed since last opening
            if (currentVersionNumber > savedVersionNumber) {
                editor.putInt(VERSION_KEY, currentVersionNumber)
                editor.apply()
                showUpdated(this@MainActivity)
            }
            editor.apply() //Finalize shared pref changes
        }
    }

    private fun scheduleJob() {
        val myJob = JobInfo.Builder(jobID, ComponentName(packageName, BackgroundJobService::class.java.name))
                .setMinimumLatency(1000)
                .setOverrideDeadline(1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setRequiresCharging(false)
                .build()

        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(myJob)
    }

    //Start a new activity for sending a feedback email
    private fun sendFeedback() {
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "text/html"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.mail_feedback_email)))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mail_feedback_subject))
        emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.mail_feedback_message))

        startActivity(Intent.createChooser(emailIntent, getString(R.string.title_send_feedback)))
        writeLog("Feedback sent", applicationContext)
    }

    private fun registerForBroadcasts(context: Context) {
        val component = ComponentName(context, ConnectivityReceiver::class.java)
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        actionBarDrawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        actionBarDrawerToggle.syncState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /*override fun onSaveInstanceState(_outState: Bundle) {
        var outState = _outState
        //add the values which need to be saved from the drawer to the bundle
        outState = slider.saveInstanceState(outState)

        //add the values which need to be saved from the accountHeader to the bundle
        outState = headerView.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }*/


    fun onBackPressed(binding: ActivityMainBinding) {
        val root = binding.root
        val slider = binding.slider
        //handle the back press :D close the drawer first and if the drawer is closed close the activity
        if (root.isDrawerOpen(slider)) {
            root.closeDrawer(slider)
        } else {
            super.onBackPressed()
        }
    }

    private fun startStandbyMode() {
        val getPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val editor = getPrefs.edit()

        if (!getPrefs.getBoolean(getString(R.string.preference_standby_dialog), false)) {
            MaterialDialog(this)
                    .icon(R.mipmap.ic_launcher)
                    .title(R.string.permissionSample, "RadioControl")
                    .positiveButton(R.string.text_ok)
                    .show()
        }

        editor.putInt(getString(R.string.preference_app_active), 0)

        val intentAction = Intent(applicationContext, ActionReceiver::class.java)
        Log.d("RadioControl-Main", "Value Changed")
        Toast.makeText(applicationContext, "Standby Mode enabled",
                Toast.LENGTH_LONG).show()

        val pIntentLogin = PendingIntent.getBroadcast(applicationContext, 1, intentAction, PendingIntent.FLAG_UPDATE_CURRENT)
        val note = NotificationCompat.Builder(applicationContext, "MainActivity")
                .setSmallIcon(R.drawable.ic_warning_black_48dp)
                .setContentTitle(getString(R.string.title_standby_dialog))
                .setContentText(getString(R.string.title_service_paused))
                //Using this action button I would like to call logTest
                .addAction(R.drawable.ic_appintro_done_white, getString(R.string.button_disable_standby), pIntentLogin)
                .setPriority(IMPORTANCE_LOW)
                .setOngoing(true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(10110, note.build())
        editor.apply()
    }

    //starts troubleshooting activity
    private fun startTroubleActivity() {
        val intent = Intent(this, TroubleshootingActivity::class.java)
        startActivity(intent)
    }

    //starts about activity
    private fun startAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)

    }

    //starts settings
    private fun startSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    //starts settings activity
    private fun startStatsActivity() {
        val intent = Intent(this, StatsActivity::class.java)
        startActivity(intent)}

    override fun onBillingReady() {
        isBillingReady = true
    }

    override fun onPurchaseFinished(
        purchaseResult: KinAppPurchaseResult,
        purchase: KinAppPurchase?
    ) {
        // Handle your purchase result here
        when (purchaseResult) {
            KinAppPurchaseResult.SUCCESS -> {
                Toast.makeText(applicationContext, R.string.donationThanks, Toast.LENGTH_LONG).show()
                Log.d("RadioControl-Main", "In-app purchase succeeded")
                val getPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val editor = getPrefs.edit()
                editor.putBoolean(getString(R.string.preference_is_donated), true)
                editor.apply()

                //billingManager.consumePurchase(purchase!!).await()

            }
            KinAppPurchaseResult.ALREADY_OWNED -> {
                Toast.makeText(applicationContext, R.string.donationExists, Toast.LENGTH_LONG).show()
                Log.d("RadioControl-Main", "Donation already purchased")
            }
            KinAppPurchaseResult.INVALID_PURCHASE -> {
                // Purchase invalid and cannot be processed
            }
            KinAppPurchaseResult.INVALID_SIGNATURE -> {
                Toast.makeText(applicationContext, R.string.donationThanks, Toast.LENGTH_LONG).show()
                Log.d("RadioControl-Main", "In-app purchase succeeded, however verification failed")
                val pref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val editor = pref.edit()
                editor.putBoolean(getString(R.string.preference_is_donated), true)
                editor.apply()
            }
            KinAppPurchaseResult.CANCEL -> {
                //Toast.makeText(MainActivity.this, R.string.donationCancel, Toast.LENGTH_LONG).show();
                Snackbar.make(findViewById(android.R.id.content), R.string.donationCancel, Snackbar.LENGTH_LONG)
                    .show()
                Log.d("RadioControl-Main", "Purchase Cancelled")
            }
        }
    }




    private fun showUpdated(c: Context) = MaterialDialog(c)
            .title(R.string.title_whats_new)
            .positiveButton(R.string.text_got_it) { dialog ->
                dialog.dismiss()
            }
            .negativeButton(R.string.text_whats_new) {
                val intent = Intent(this, ChangeLogActivity::class.java)
                startActivity(intent)
            }
            .show()

    //donate dialog
    private fun showDonateDialog() {
        val view = inflate(applicationContext, R.layout.dialog_donate, null)//Initializes the view for donate dialog
        val builder = AlertDialog.Builder(this)//creates alertdialog


        builder.setView(view).setTitle(R.string.donate)//sets title
                .setPositiveButton(R.string.cancel) { dialog, _ ->
                    Log.v("RadioControl-Main", "Donation Cancelled")
                    dialog.dismiss()
                }

        val alert = builder.create()
        alert.show()

        //Sets the purchase options
        val oneButton = view.findViewById<Button>(R.id.oneDollar)
        oneButton.setOnClickListener {
            alert.cancel() //Closes the donate dialog
            billingManager.purchase(this, ITEM_ONE_DOLLAR, KinAppProductType.INAPP)
        }
        val threeButton = view.findViewById<Button>(R.id.threeDollar)
        threeButton.setOnClickListener {
            alert.cancel() //Closes the donate dialog
            billingManager.purchase(this, ITEM_THREE_DOLLAR, KinAppProductType.INAPP)
        }
        val fiveButton = view.findViewById<Button>(R.id.fiveDollar)
        fiveButton.setOnClickListener {
            alert.cancel() //Closes the donate dialog
            billingManager.purchase(this, ITEM_FIVE_DOLLAR, KinAppProductType.INAPP)
        }
        val tenButton = view.findViewById<Button>(R.id.tenDollar)
        tenButton.setOnClickListener {
            alert.cancel() //Closes the donate dialog
            billingManager.purchase(this, ITEM_TEN_DOLLAR, KinAppProductType.INAPP)
        }


    }

    //Internet Error dialog
    private fun showErrorDialog() {
        val view = inflate(applicationContext, R.layout.dialog_no_internet, null)//Initializes the view for error dialog
        val builder = AlertDialog.Builder(this)//creates alertdialog
        val title = TextView(this)

        // You Can Customise your Title here
        title.setText(R.string.noInternet)
        title.setBackgroundColor(Color.DKGRAY)
        title.setPadding(10, 10, 10, 10)
        title.gravity = Gravity.CENTER
        title.setTextColor(Color.WHITE)
        title.textSize = 20f

        builder.setCustomTitle(title)
        builder.setView(view)
            .setPositiveButton(R.string.text_ok) { dialog, _ -> dialog.dismiss() }

        builder.create().show()
    }

    override fun onResume() {
        super.onResume()
        //  TODO Figure out how to take init code and run without duplication
        val getPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val pref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        //If workmode is false
        if (!pref.getBoolean(getString(R.string.preference_work_mode), true)) {
            registerForBroadcasts(applicationContext)
        }
        //drawerCreate()

        //Connection Test button (Dev Feature)
        val conn = findViewById<Button>(R.id.pingTestButton)
        val serviceTest = findViewById<Button>(R.id.airplane_service_test)
        val nightCancel = findViewById<Button>(R.id.night_mode_cancel)
        val radioOffButton = findViewById<Button>(R.id.cellRadioOff)
        val forceCrashButton = findViewById<Button>(R.id.forceCrashButton)
        val connectionStatusText = findViewById<TextView>(R.id.pingStatus)
        //LinkSpeed Button
        val btn3 = findViewById<Button>(R.id.linkSpeedButton)
        val linkText = findViewById<TextView>(R.id.linkSpeed)
        val statusText = findViewById<TextView>(R.id.statusText)
        val toggle = findViewById<SwitchMaterial>(R.id.enableSwitch)

        //Check if the easter egg is NOT activated
        if (!getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            conn.visibility = View.GONE
            serviceTest.visibility = View.GONE
            nightCancel.visibility = View.GONE
            connectionStatusText.visibility = View.GONE
            radioOffButton.visibility = View.GONE
            forceCrashButton.visibility = View.GONE
            btn3.visibility = View.GONE
            linkText.visibility = View.GONE
        } else if (getPrefs.getBoolean(getString(R.string.preference_is_developer), false)) {
            conn.visibility = View.VISIBLE
            serviceTest.visibility = View.VISIBLE
            nightCancel.visibility = View.VISIBLE
            connectionStatusText.visibility = View.VISIBLE
            radioOffButton.visibility = View.VISIBLE
            forceCrashButton.visibility = View.VISIBLE
            btn3.visibility = View.VISIBLE
            linkText.visibility = View.VISIBLE
        }

        /*if (!Shell.rootAccess()) {
            //toggle.isEnabled = false
            statusText.setText(R.string.noRoot)
            statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
        }*/

        if (getPrefs.getInt(getString(R.string.preference_app_active), 0) == 1) {
            if (!Shell.rootAccess()) {
                toggle.isEnabled = false
                statusText.setText(R.string.noRoot)
                statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_no_root))
            } else {
                statusText.setText(R.string.rEnabled)
                statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                toggle.isChecked = true
            }

        } else if (getPrefs.getInt(getString(R.string.preference_app_active), 0) == 0) {
            if (!Shell.rootAccess()) {
                //toggle.isEnabled = false
                statusText.setText(R.string.noRoot)
                statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_no_root))
            } else {
                statusText.setText(R.string.rDisabled)
                statusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
                toggle.isChecked = false
            }

        }
    }

    private fun writeLog(data: String, c: Context) {
        if (androidx.preference.PreferenceManager.getDefaultSharedPreferences(c).getBoolean("enableLogs", false)) {
            try {
                val h = DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis()).toString()
                val log = File(c.filesDir, "radiocontrol.log")
                if (!log.exists()) {
                    log.createNewFile()
                }
                val logPath = "radiocontrol.log"
                val string = "\n$h: $data"

                val fos = c.openFileOutput(logPath, Context.MODE_APPEND)
                fos.write(string.toByteArray())
                fos.close()
            } catch (e: IOException) {
                Log.e("RadioControl-Main", "Error saving log")
            }
        }
    }

    private suspend fun pingCheck() {
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val ip = preferences.getString("prefPingIp", "1.0.0.1")
        val address = InetAddress.getByName(ip)
        var reachable = false

        val timeDifference = measureTimeMillis {
            reachable = address.isReachable(1000)
        }
        Log.d("RadioControl-Main", "Reachable?: $reachable, Time: $timeDifference")

        val dialog: ProgressBar = findViewById(R.id.pingProgressBar)
        dialog.visibility = View.GONE
        val connectionStatusText = findViewById<TextView>(R.id.pingStatus)
        if (reachable) {
            when {
                timeDifference <= 50 -> Snackbar.make(clayout, "Excellent Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                51.0 <= timeDifference && timeDifference <= 100.0 -> Snackbar.make(clayout, "Average Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                101.0 <= timeDifference && timeDifference <= 200.0 -> Snackbar.make(clayout, "Poor Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                timeDifference >= 201 -> Snackbar.make(clayout, "Very Poor Latency. VOIP and online gaming may suffer: $timeDifference ms", Snackbar.LENGTH_LONG).show()
            }
        }
        //Sadly packet loss testing is gone :(
        /*//Check for packet loss stuff
        when {
            pStatus!!.contains("100% packet loss") -> Snackbar.make(clayout, "100% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("25% packet loss") -> Snackbar.make(clayout, "25% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("50% packet loss") -> Snackbar.make(clayout, "50% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("75% packet loss") -> Snackbar.make(clayout, "75% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("unknown host") -> Snackbar.make(clayout, "Unknown host", Snackbar.LENGTH_LONG).show()
        }*/
        //  TODO:Move to NetworkUtilities class
        if (reachable) {
            if (Utilities.isConnectedWifi(applicationContext)) {
                // TODO Return info back to main activity
                connectionStatusText.setText(R.string.connectedWifi)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                writeLog(getString(R.string.connectedWifi), applicationContext)
                // TODO move UI elements to react to return statements
            } else if (Utilities.isConnectedMobile(applicationContext)) {
                if (Utilities.isConnectedFast(applicationContext)) {
                    connectionStatusText.setText(R.string.connectedFCell)
                    connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                    writeLog(getString(R.string.connectedFCell), applicationContext)
                } else if (!Utilities.isConnectedFast(applicationContext)) {
                    connectionStatusText.setText(R.string.connectedSCell)
                    connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                    writeLog(getString(R.string.connectedSCell), applicationContext)
                }
            }

        } else {
            if (Utilities.isAirplaneMode(applicationContext) && !Utilities.isConnected(applicationContext)) {
                connectionStatusText.setText(R.string.airplaneOn)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
            } else {
                connectionStatusText.setText(R.string.connectionUnable)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
                writeLog(getString(R.string.connectionUnable), applicationContext)
            }
        }
        // END TODO: Move to NetworkUtilities class
    }
    /*
    private suspend fun pingCheckUI() {
        val dialog: ProgressBar = findViewById(R.id.pingProgressBar)
        dialog.visibility = View.GONE
        val connectionStatusText = findViewById<TextView>(R.id.pingStatus)

        when {
            NetworkUtility.reachable(applicationContext) -> {
                when {
                    timeDifference <= 50 -> Snackbar.make(clayout, "Excellent Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                    51.0 <= timeDifference && timeDifference <= 100.0 -> Snackbar.make(clayout, "Average Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                    101.0 <= timeDifference && timeDifference <= 200.0 -> Snackbar.make(clayout, "Poor Latency: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                    timeDifference >= 201 -> Snackbar.make(clayout, "Very Poor Latency. VOIP and online gaming may suffer: $timeDifference ms", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        //Sadly packet loss testing is gone :(
        /*//Check for packet loss stuff
        when {
            pStatus!!.contains("100% packet loss") -> Snackbar.make(clayout, "100% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("25% packet loss") -> Snackbar.make(clayout, "25% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("50% packet loss") -> Snackbar.make(clayout, "50% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("75% packet loss") -> Snackbar.make(clayout, "75% packet loss detected", Snackbar.LENGTH_LONG).show()
            pStatus.contains("unknown host") -> Snackbar.make(clayout, "Unknown host", Snackbar.LENGTH_LONG).show()
        }*/
        //  TODO: Refactor into NetworkUtilities class
        if (reachable) {
            if (Utilities.isConnectedWifi(applicationContext)) {
                // TODO Return info back to main activity
                connectionStatusText.setText(R.string.connectedWifi)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                writeLog(getString(R.string.connectedWifi), applicationContext)
                // TODO move UI elements to react to return statements
            } else if (Utilities.isConnectedMobile(applicationContext)) {
                if (Utilities.isConnectedFast(applicationContext)) {
                    connectionStatusText.setText(R.string.connectedFCell)
                    connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                    writeLog(getString(R.string.connectedFCell), applicationContext)
                } else if (!Utilities.isConnectedFast(applicationContext)) {
                    connectionStatusText.setText(R.string.connectedSCell)
                    connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_activated))
                    writeLog(getString(R.string.connectedSCell), applicationContext)
                }
            }

        } else {
            if (Utilities.isAirplaneMode(applicationContext) && !Utilities.isConnected(applicationContext)) {
                connectionStatusText.setText(R.string.airplaneOn)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
            } else {
                connectionStatusText.setText(R.string.connectionUnable)
                connectionStatusText.setTextColor(ContextCompat.getColor(applicationContext, R.color.status_deactivated))
                writeLog(getString(R.string.connectionUnable), applicationContext)
            }
        }
        // END TODO: Refactor into NetworkUtilities class
    }*/

    override fun onStart() {
        super.onStart()
        // Start service and provide it a way to communicate with this class.
        val startServiceIntent = Intent(this, BackgroundJobService::class.java)
        startService(startServiceIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!billingManager.verifyPurchase(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        billingManager.unbind()
    }
}

    /** Archived code **/
    /*fun onStop() {
        // A service can be "started" and/or "bound". In this case, it's "started" by this Activity
        // and "bound" to the JobScheduler (also called "Scheduled" by the JobScheduler). This call
        // to stopService() won't prevent scheduled jobs to be processed. However, failing
        // to call stopService() would keep it alive indefinitely.
        //stopService(Intent(this, BackgroundJobService::class.java))
        super.onStop()
    }*/




