package com.smsbomb.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Foreground service that sends an SMS every [SendConfig.intervalMillis].
 * Uses a [Handler] loop driven by [SmsSchedulerEngine.millisUntilNextEvent].
 */
class SmsSchedulerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var engine: SmsSchedulerEngine? = null

    private val tick = object : Runnable {
        override fun run() {
            val current = engine ?: return
            val result = current.onTick()
            if (result != null) {
                SharedSendLog.instance.add(
                    LogEntry(
                        timestampMillis = result.timestampMillis,
                        phoneNumber = result.phoneNumber,
                        messagePreview = result.message.take(50),
                        success = result.success,
                        error = result.error,
                    )
                )
                SchedulerState.recordSend(
                    result,
                    current.millisUntilNextSend()?.let { System.currentTimeMillis() + it },
                )
                Log.d(TAG, "[SCHED] attempt complete success=${result.success} sent=${current.sentCount}")
            }
            if (!current.running) {
                stopScheduler(current.stopReason)
                return
            }
            updateNotification()
            scheduleNextTick()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopScheduler()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val config = SendConfig(
                    phoneNumber = intent.getStringExtra(EXTRA_NUMBER).orEmpty(),
                    message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                    intervalMillis = intent.getLongExtra(EXTRA_INTERVAL_MILLIS, 0L),
                    randomFactorPercent = intent.getIntExtra(EXTRA_RANDOM_FACTOR_PERCENT, 0),
                    endAtMillis = if (intent.hasExtra(EXTRA_END_AT_MILLIS)) {
                        intent.getLongExtra(EXTRA_END_AT_MILLIS, 0L)
                    } else {
                        null
                    },
                    maxSmsCount = if (intent.hasExtra(EXTRA_MAX_SMS_COUNT)) {
                        intent.getIntExtra(EXTRA_MAX_SMS_COUNT, 0)
                    } else {
                        null
                    },
                )
                val errors = config.validationErrors()
                if (errors.isNotEmpty()) {
                    Log.w(TAG, "[SCHED] invalid config, refusing to start: $errors")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startScheduler(config)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startScheduler(config: SendConfig) {
        ensureNotificationChannel()
        SchedulerState.markRunning(nextSendAt = null)
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_starting)))

        engine = SmsSchedulerEngine(
            config = config,
            sender = AndroidSmsSender(this),
            clock = System::currentTimeMillis,
        ).also { it.start() }

        if (engine?.running != true) {
            stopScheduler(engine?.stopReason)
            return
        }

        SchedulerState.markRunning(
            nextSendAt = engine?.millisUntilNextSend()?.let { System.currentTimeMillis() + it }
        )
        Log.i(
            TAG,
            "[SCHED] started: number=${config.phoneNumber} interval=${config.intervalMillis}ms " +
                "random=${config.randomFactorPercent}% endAt=${config.endAtMillis} maxSms=${config.maxSmsCount}",
        )
        scheduleNextTick()
    }

    private fun stopScheduler(
        reason: SmsSchedulerEngine.StopReason? = SmsSchedulerEngine.StopReason.USER_STOP,
    ) {
        handler.removeCallbacks(tick)
        if (engine?.running == true) engine?.stop()
        engine = null
        SchedulerState.markStopped(reason)
        Log.i(TAG, "[SCHED] stopped: reason=$reason")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleNextTick() {
        val delay = engine?.millisUntilNextEvent() ?: return
        handler.postDelayed(tick, delay.coerceAtLeast(MIN_TICK_DELAY_MILLIS))
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (SchedulerState.running) {
            SchedulerState.markStopped(SmsSchedulerEngine.StopReason.USER_STOP)
        }
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val count = engine?.sentCount ?: 0
        val notification = buildNotification(
            getString(R.string.notification_sent_count, count)
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "SmsSchedulerService"
        private const val CHANNEL_ID = "sms_scheduler"
        private const val NOTIFICATION_ID = 1
        private const val MIN_TICK_DELAY_MILLIS = 250L

        const val ACTION_START = "com.smsbomb.app.action.START"
        const val ACTION_STOP = "com.smsbomb.app.action.STOP"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_INTERVAL_MILLIS = "extra_interval_millis"
        const val EXTRA_RANDOM_FACTOR_PERCENT = "extra_random_factor_percent"
        const val EXTRA_END_AT_MILLIS = "extra_end_at_millis"
        const val EXTRA_MAX_SMS_COUNT = "extra_max_sms_count"

        fun startIntent(context: Context, config: SendConfig): Intent =
            Intent(context, SmsSchedulerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NUMBER, config.phoneNumber)
                .putExtra(EXTRA_MESSAGE, config.message)
                .putExtra(EXTRA_INTERVAL_MILLIS, config.intervalMillis)
                .putExtra(EXTRA_RANDOM_FACTOR_PERCENT, config.randomFactorPercent)
                .apply {
                    config.endAtMillis?.let { putExtra(EXTRA_END_AT_MILLIS, it) }
                    config.maxSmsCount?.let { putExtra(EXTRA_MAX_SMS_COUNT, it) }
                }

        fun stopIntent(context: Context): Intent =
            Intent(context, SmsSchedulerService::class.java).setAction(ACTION_STOP)
    }
}
