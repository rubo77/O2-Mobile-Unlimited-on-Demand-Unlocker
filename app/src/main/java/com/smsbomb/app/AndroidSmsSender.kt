package com.smsbomb.app

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

/** [SmsSender] backed by the platform [SmsManager]; splits long texts into parts. */
class AndroidSmsSender(private val context: Context) : SmsSender {

    private val smsManager: SmsManager
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    override fun send(phoneNumber: String, message: String): SendResult {
        return try {
            val manager = smsManager
            val parts = manager.divideMessage(message)
            if (parts.size <= 1) {
                manager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            SendResult(phoneNumber, message, System.currentTimeMillis(), success = true)
        } catch (e: Exception) {
            Log.e(TAG, "[SMS] send failed to $phoneNumber", e)
            SendResult(phoneNumber, message, System.currentTimeMillis(), success = false, error = e.message)
        }
    }

    private companion object {
        const val TAG = "SmsSender"
    }
}
