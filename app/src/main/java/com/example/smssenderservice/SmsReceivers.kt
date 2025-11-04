package com.example.smssenderservice

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_SMS_SENT = "com.example.smssenderservice.SMS_SENT"
const val ACTION_SMS_DELIVERED = "com.example.smssenderservice.SMS_DELIVERED"

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val jobId = intent?.getStringExtra("jobId")
        val messageId = intent?.getStringExtra("messageId")
        if (jobId == null || messageId == null) return

        val status = when (resultCode) {
            Activity.RESULT_OK -> MessageSendStatus.SENT
            else -> MessageSendStatus.ERROR
        }
        Log.d("SmsSentReceiver", "Odebrano raport WYSŁANIA dla $messageId. Status: ${status.name}, Kod: $resultCode")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context?.let {
                    SentMessagesRepository.updateStatus(it, messageId, status)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val jobId = intent?.getStringExtra("jobId")
        val messageId = intent?.getStringExtra("messageId")
        if (jobId == null || messageId == null) return

        Log.d("SmsDeliveredReceiver", "Odebrano raport DOSTARCZENIA dla $messageId. Raport ignorowany zgodnie z konfiguracją.")
    }
}

// NOWA KLASA: Wymagana przez Androida dla domyślnej aplikacji SMS
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                // Aplikacja musi obsłużyć przychodzące SMSy.
                // Zgodnie z życzeniem, nie robimy nic poza logowaniem.
                Log.d("SmsReceiver", "Odebrano przychodzący SMS od: ${sms.originatingAddress} treść: ${sms.messageBody}")
            }
        }
    }
}
