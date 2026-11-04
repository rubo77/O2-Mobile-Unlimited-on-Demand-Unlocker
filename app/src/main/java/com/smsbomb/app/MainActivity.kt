package com.smsbomb.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toolbar
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var etNumber: EditText
    private lateinit var etMessage: EditText
    private lateinit var etInterval: EditText
    private lateinit var etRandomFactor: EditText
    private lateinit var etMaxSmsCount: EditText
    private lateinit var spinnerUnit: Spinner
    private lateinit var btnAdvanced: Button
    private lateinit var advancedFields: LinearLayout
    private lateinit var cbEndAt: CheckBox
    private lateinit var btnEndDate: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnToggle: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    private enum class PendingAction { START, TEST }
    private var pendingAction: PendingAction? = null
    private var advancedExpanded = false
    private var selectedEndAtMillis = defaultEndAtMillis()

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setActionBar(toolbar)
        toolbar.overflowIcon = getDrawable(R.drawable.ic_hamburger)

        etNumber = findViewById(R.id.etNumber)
        etMessage = findViewById(R.id.etMessage)
        etInterval = findViewById(R.id.etInterval)
        etRandomFactor = findViewById(R.id.etRandomFactor)
        etMaxSmsCount = findViewById(R.id.etMaxSmsCount)
        spinnerUnit = findViewById(R.id.spinnerUnit)
        btnAdvanced = findViewById(R.id.btnAdvanced)
        advancedFields = findViewById(R.id.advancedFields)
        cbEndAt = findViewById(R.id.cbEndAt)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnToggle = findViewById(R.id.btnToggle)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

        ArrayAdapter.createFromResource(
            this,
            R.array.interval_units,
            android.R.layout.simple_spinner_item,
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUnit.adapter = adapter
        }

        restoreInputs()
        btnToggle.setOnClickListener { onToggleClicked() }
        btnTest.setOnClickListener { onTestClicked() }
        btnAdvanced.setOnClickListener { setAdvancedExpanded(!advancedExpanded) }
        cbEndAt.setOnCheckedChangeListener { _, checked -> setEndDateControlsEnabled(checked) }
        btnEndDate.setOnClickListener { showEndDatePicker() }
        btnEndTime.setOnClickListener { showEndTimePicker() }
        updateEndDateTimeButtons()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun onToggleClicked() {
        pendingAction = PendingAction.START
        if (SchedulerState.running) {
            startService(SmsSchedulerService.stopIntent(this))
            return
        }
        val config = buildConfig() ?: return
        if (!ensurePermissions()) return
        saveInputs()
        startForegroundService(SmsSchedulerService.startIntent(this, config))
    }

    private fun onTestClicked() {
        pendingAction = PendingAction.TEST
        val number = etNumber.text.toString().trim()
        val message = etMessage.text.toString()
        val dummyConfig = SendConfig(number, message, intervalMillis = 600_000L)
        val error = dummyConfig.validationErrors().firstOrNull {
            it == SendConfig.ValidationError.EMPTY_NUMBER ||
            it == SendConfig.ValidationError.INVALID_NUMBER ||
            it == SendConfig.ValidationError.EMPTY_MESSAGE
        }
        if (error != null) {
            val resId = when (error) {
                SendConfig.ValidationError.EMPTY_NUMBER -> R.string.error_empty_number
                SendConfig.ValidationError.INVALID_NUMBER -> R.string.error_invalid_number
                SendConfig.ValidationError.EMPTY_MESSAGE -> R.string.error_empty_message
                else -> R.string.error_empty_number
            }
            showError(getString(resId))
            return
        }

        if (!ensurePermissions()) return
        saveInputs()
        sendTestSms(number, message)
    }

    private fun sendTestSms(number: String, message: String) {
        btnTest.isEnabled = false
        Thread {
            val sender = AndroidSmsSender(this)
            val result = sender.send(number, message)
            SharedSendLog.instance.add(
                LogEntry(
                    timestampMillis = result.timestampMillis,
                    phoneNumber = result.phoneNumber,
                    messagePreview = result.message.take(50),
                    success = result.success,
                    error = result.error,
                )
            )
            handler.post {
                if (!isFinishing && !isDestroyed) {
                    btnTest.isEnabled = !SchedulerState.running
                    refreshUi()
                }
            }
        }.start()
    }

    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        val message = "${getString(R.string.app_name)}\n${getString(R.string.about_version, versionName)}\n\nhttps://github.com/rubo77/sms-bomb"

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(message)
            .setPositiveButton(R.string.about_github) { _, _ ->
                openUrl("https://github.com/rubo77/sms-bomb")
            }
            .setNegativeButton(android.R.string.ok, null)

        val dialog = builder.create()
        dialog.show()

        val tvMessage = dialog.findViewById<TextView>(android.R.id.message)
        tvMessage?.let {
            Linkify.addLinks(it, Linkify.WEB_URLS)
            it.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL $url", e)
        }
    }

    private fun buildConfig(): SendConfig? {
        val number = etNumber.text.toString().trim()
        val message = etMessage.text.toString()
        val unit = IntervalUnit.entries[spinnerUnit.selectedItemPosition]
        val intervalMillis = IntervalParser.toMillis(etInterval.text.toString(), unit)
        val randomFactorPercent = etRandomFactor.text.toString().trim().toIntOrNull()
        val maxSmsText = etMaxSmsCount.text.toString().trim()
        val maxSmsCount = if (maxSmsText.isEmpty()) null else maxSmsText.toIntOrNull()

        if (intervalMillis == null) {
            showError(getString(R.string.error_invalid_interval))
            return null
        }
        if (randomFactorPercent == null) {
            showError(getString(R.string.error_invalid_random_factor))
            return null
        }
        if (maxSmsText.isNotEmpty() && maxSmsCount == null) {
            showError(getString(R.string.error_invalid_max_sms_count))
            return null
        }
        val config = SendConfig(
            phoneNumber = number,
            message = message,
            intervalMillis = intervalMillis,
            randomFactorPercent = randomFactorPercent,
            endAtMillis = if (cbEndAt.isChecked) selectedEndAtMillis else null,
            maxSmsCount = maxSmsCount,
        )
        val errors = config.validationErrors()
        if (errors.isNotEmpty()) {
            val resId = when (errors.first()) {
                SendConfig.ValidationError.EMPTY_NUMBER -> R.string.error_empty_number
                SendConfig.ValidationError.INVALID_NUMBER -> R.string.error_invalid_number
                SendConfig.ValidationError.EMPTY_MESSAGE -> R.string.error_empty_message
                SendConfig.ValidationError.NON_POSITIVE_INTERVAL -> R.string.error_invalid_interval
                SendConfig.ValidationError.INTERVAL_TOO_SHORT -> R.string.error_interval_too_short
                SendConfig.ValidationError.INVALID_RANDOM_FACTOR -> R.string.error_invalid_random_factor
                SendConfig.ValidationError.END_TIME_IN_PAST -> R.string.error_end_time_in_past
                SendConfig.ValidationError.INVALID_MAX_SMS_COUNT -> R.string.error_invalid_max_sms_count
            }
            showError(getString(resId))
            return null
        }
        return config
    }

    private fun ensurePermissions(): Boolean {
        val needed = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        Log.i(TAG, "[PERM] requesting: $missing")
        requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        showError(getString(R.string.status_permissions_needed))
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.i(TAG, "[PERM] granted, retrying action")
            when (pendingAction) {
                PendingAction.START -> onToggleClicked()
                PendingAction.TEST -> onTestClicked()
                null -> {}
            }
        } else {
            Log.w(TAG, "[PERM] denied: ${permissions.toList()}")
            showError(getString(R.string.error_permissions_denied))
        }
    }

    private fun refreshUi() {
        btnToggle.text = getString(
            if (SchedulerState.running) R.string.action_stop else R.string.action_start
        )
        setInputsEnabled(!SchedulerState.running)
        btnTest.isEnabled = !SchedulerState.running

        tvStatus.text = when {
            !SchedulerState.running -> when (SchedulerState.lastStopReason) {
                SmsSchedulerEngine.StopReason.END_TIME ->
                    getString(R.string.status_stopped_end_time, SchedulerState.sentCount)
                SmsSchedulerEngine.StopReason.MAX_SMS_COUNT ->
                    getString(R.string.status_stopped_sms_count, SchedulerState.sentCount)
                else -> getString(R.string.status_idle)
            }
            else -> {
                val next = SchedulerState.nextSendAtMillis
                val countdown = next?.let {
                    IntervalParser.format((it - System.currentTimeMillis()).coerceAtLeast(0L))
                } ?: "?"
                val base = getString(R.string.status_running, SchedulerState.sentCount, countdown)
                val error = SchedulerState.lastError
                if (error != null) "$base – ${getString(R.string.status_last_error, error)}" else base
            }
        }
        tvStatus.visibility = View.VISIBLE

        tvLog.text = SharedSendLog.instance.entries()
            .asReversed()
            .joinToString("\n") { entry -> formatLogEntry(entry) }
    }

    private fun formatLogEntry(entry: LogEntry): String {
        val time = TIME_FORMAT.format(Date(entry.timestampMillis))
        val mark = if (entry.success) "OK" else "FAIL(${entry.error})"
        return "$time  $mark  → ${entry.phoneNumber}: ${entry.messagePreview}"
    }

    private fun setInputsEnabled(enabled: Boolean) {
        etNumber.isEnabled = enabled
        etMessage.isEnabled = enabled
        etInterval.isEnabled = enabled
        etRandomFactor.isEnabled = enabled
        etMaxSmsCount.isEnabled = enabled
        spinnerUnit.isEnabled = enabled
        cbEndAt.isEnabled = enabled
        setEndDateControlsEnabled(enabled && cbEndAt.isChecked)
    }

    private fun setEndDateControlsEnabled(enabled: Boolean) {
        btnEndDate.isEnabled = enabled
        btnEndTime.isEnabled = enabled
    }

    private fun setAdvancedExpanded(expanded: Boolean) {
        advancedExpanded = expanded
        advancedFields.visibility = if (expanded) View.VISIBLE else View.GONE
        btnAdvanced.contentDescription = getString(
            if (expanded) R.string.advanced_expanded else R.string.advanced_collapsed
        )
    }

    private fun showEndDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedEndAtMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = selectedEndAtMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                selectedEndAtMillis = updated.timeInMillis
                updateEndDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun showEndTimePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedEndAtMillis }
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = selectedEndAtMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedEndAtMillis = updated.timeInMillis
                updateEndDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun updateEndDateTimeButtons() {
        val date = Date(selectedEndAtMillis)
        btnEndDate.text = getString(
            R.string.end_date_value,
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(date),
        )
        btnEndTime.text = getString(
            R.string.end_time_value,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date),
        )
    }

    private fun showError(text: String) {
        tvStatus.text = text
        tvStatus.visibility = View.VISIBLE
    }

    private fun restoreInputs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etNumber.setText(prefs.getString(KEY_NUMBER, DEFAULT_NUMBER))
        etMessage.setText(prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE))
        etInterval.setText(prefs.getString(KEY_INTERVAL, DEFAULT_INTERVAL))
        etRandomFactor.setText(prefs.getString(KEY_RANDOM_FACTOR, DEFAULT_RANDOM_FACTOR))
        etMaxSmsCount.setText(prefs.getString(KEY_MAX_SMS_COUNT, ""))
        spinnerUnit.setSelection(prefs.getInt(KEY_UNIT, IntervalUnit.MINUTES.ordinal))
        cbEndAt.isChecked = prefs.getBoolean(KEY_END_ENABLED, false)
        selectedEndAtMillis = prefs.getLong(KEY_END_AT, defaultEndAtMillis())
    }

    private fun saveInputs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_NUMBER, etNumber.text.toString().trim())
            .putString(KEY_MESSAGE, etMessage.text.toString())
            .putString(KEY_INTERVAL, etInterval.text.toString().trim())
            .putString(KEY_RANDOM_FACTOR, etRandomFactor.text.toString().trim())
            .putString(KEY_MAX_SMS_COUNT, etMaxSmsCount.text.toString().trim())
            .putInt(KEY_UNIT, spinnerUnit.selectedItemPosition)
            .putBoolean(KEY_END_ENABLED, cbEndAt.isChecked)
            .putLong(KEY_END_AT, selectedEndAtMillis)
            .apply()
    }

    private fun defaultEndAtMillis(): Long =
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis

    private companion object {
        const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 42
        const val PREFS_NAME = "sms_bomb_config"
        const val KEY_NUMBER = "number"
        const val KEY_MESSAGE = "message"
        const val KEY_INTERVAL = "interval"
        const val KEY_UNIT = "unit"
        const val KEY_RANDOM_FACTOR = "random_factor"
        const val KEY_MAX_SMS_COUNT = "max_sms_count"
        const val KEY_END_ENABLED = "end_enabled"
        const val KEY_END_AT = "end_at"

        const val DEFAULT_NUMBER = "80112"
        const val DEFAULT_MESSAGE = "WEITER"
        const val DEFAULT_INTERVAL = "30"
        const val DEFAULT_RANDOM_FACTOR = "10"
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
