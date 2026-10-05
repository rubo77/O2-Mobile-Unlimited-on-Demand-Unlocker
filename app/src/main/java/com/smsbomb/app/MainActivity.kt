package com.smsbomb.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var etNumber: EditText
    private lateinit var etMessage: EditText
    private lateinit var etInterval: EditText
    private lateinit var spinnerUnit: Spinner
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

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

        etNumber = findViewById(R.id.etNumber)
        etMessage = findViewById(R.id.etMessage)
        etInterval = findViewById(R.id.etInterval)
        spinnerUnit = findViewById(R.id.spinnerUnit)
        btnToggle = findViewById(R.id.btnToggle)
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
        if (SchedulerState.running) {
            startService(SmsSchedulerService.stopIntent(this))
            return
        }
        val config = buildConfig() ?: return
        if (!ensurePermissions()) return
        saveInputs()
        startForegroundService(SmsSchedulerService.startIntent(this, config))
    }

    private fun buildConfig(): SendConfig? {
        val number = etNumber.text.toString().trim()
        val message = etMessage.text.toString()
        val unit = IntervalUnit.entries[spinnerUnit.selectedItemPosition]
        val intervalMillis = IntervalParser.toMillis(etInterval.text.toString(), unit)

        if (intervalMillis == null) {
            showError(getString(R.string.error_invalid_interval))
            return null
        }
        val config = SendConfig(number, message, intervalMillis)
        val errors = config.validationErrors()
        if (errors.isNotEmpty()) {
            val resId = when (errors.first()) {
                SendConfig.ValidationError.EMPTY_NUMBER -> R.string.error_empty_number
                SendConfig.ValidationError.INVALID_NUMBER -> R.string.error_invalid_number
                SendConfig.ValidationError.EMPTY_MESSAGE -> R.string.error_empty_message
                SendConfig.ValidationError.NON_POSITIVE_INTERVAL -> R.string.error_invalid_interval
                SendConfig.ValidationError.INTERVAL_TOO_SHORT -> R.string.error_interval_too_short
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
            Log.i(TAG, "[PERM] granted, retrying start")
            onToggleClicked()
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

        tvStatus.text = when {
            !SchedulerState.running -> getString(R.string.status_idle)
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
        spinnerUnit.isEnabled = enabled
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
        spinnerUnit.setSelection(prefs.getInt(KEY_UNIT, IntervalUnit.MINUTES.ordinal))
    }

    private fun saveInputs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_NUMBER, etNumber.text.toString().trim())
            .putString(KEY_MESSAGE, etMessage.text.toString())
            .putString(KEY_INTERVAL, etInterval.text.toString().trim())
            .putInt(KEY_UNIT, spinnerUnit.selectedItemPosition)
            .apply()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 42
        const val PREFS_NAME = "sms_bomb_config"
        const val KEY_NUMBER = "number"
        const val KEY_MESSAGE = "message"
        const val KEY_INTERVAL = "interval"
        const val KEY_UNIT = "unit"

        // Defaults target the O2 "Unlimited On Demand" top-up workflow:
        // texting "WEITER" to 80112 books another free 2 GB high-speed block.
        const val DEFAULT_NUMBER = "80112"
        const val DEFAULT_MESSAGE = "WEITER"
        const val DEFAULT_INTERVAL = "30"
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
