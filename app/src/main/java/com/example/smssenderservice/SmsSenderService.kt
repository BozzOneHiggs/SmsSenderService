package com.example.smssenderservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSenderService : Service() {

    private lateinit var db: FirebaseFirestore
    private var firestoreListener: ListenerRegistration? = null
    private val NOTIFICATION_CHANNEL_ID = "SmsSenderServiceChannel"
    private val SENT_SMS_ACTION = "com.example.smssenderservice.SMS_SENT"
    private val DELIVERED_SMS_ACTION = "com.example.smssenderservice.SMS_DELIVERED"

    private val syncHandler = Handler(Looper.getMainLooper())
    private lateinit var syncRunnable: Runnable
    private val SYNC_INTERVAL_MS = 60000L // Synchronizuj co 60 sekund

    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        createNotificationChannel()
        setupPeriodicSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Nasłuchiwanie na nowe zlecenia SMS...")
        startForeground(1, notification)
        listenForSmsJobs()
        syncHandler.post(syncRunnable)
        return START_STICKY
    }

    private fun setupPeriodicSync() {
        syncRunnable = Runnable {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d("SmsSenderService", "Uruchamiam okresową synchronizację statusów...")
                SmsStatusSyncer.sync(this@SmsSenderService)
                syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL_MS)
            }
        }
    }

    private fun listenForSmsJobs() {
        if (firestoreListener != null) return
        updateNotification("Oczekuję na nowe zlecenia...")
        val query = db.collection("smsJobs")
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(1)

        firestoreListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                updateNotification("Błąd nasłuchiwania: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshots != null && !snapshots.isEmpty) {
                val jobDocument = snapshots.documents[0]
                val jobId = jobDocument.id
                updateNotification("Przetwarzanie zlecenia: $jobId")

                db.collection("smsJobs").document(jobId).update("status", "processing")
                    .addOnSuccessListener {
                        prepareAndSendMessages(jobId, jobDocument.get("messages") as? List<Map<String, String>>)
                    }
            } else {
                updateNotification("Oczekuję na nowe zlecenia...")
            }
        }
    }

    private fun prepareAndSendMessages(jobId: String, messages: List<Map<String, String>>?) {
        if (messages == null) {
            db.collection("smsJobs").document(jobId).update("status", "error_malformed_data")
            return
        }
        val updatedMessages = messages.mapIndexed { index, msg ->
            mapOf(
                "id" to "msg_${System.currentTimeMillis()}_${index}",
                "phone" to msg["phone"],
                "message" to msg["message"],
                "status" to "sending"
            )
        }
        db.collection("smsJobs").document(jobId).update("messages", updatedMessages)
            .addOnSuccessListener {
                sendAllSms(jobId, updatedMessages)
            }
    }

    private fun sendAllSms(jobId: String, messages: List<Map<String, String?>>) {
        val smsManager = this.getSystemService(SmsManager::class.java)
        var requestCodeCounter = System.currentTimeMillis().toInt()

        for ((index, msg) in messages.withIndex()) {
            val phone = msg["phone"]
            var messageContent = msg["message"]
            val messageId = msg["id"]

            if (phone != null && messageContent != null && messageId != null) {
                messageContent = messageContent.replace("&", "\n")

                val parts = smsManager.divideMessage(messageContent)
                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()

                for (i in parts.indices) {
                    val sentRequestCode = requestCodeCounter++
                    val deliveredRequestCode = requestCodeCounter++

                    val sentIntent = Intent(SENT_SMS_ACTION).apply {
                        setClass(this@SmsSenderService, SmsSentReceiver::class.java)
                        putExtra("jobId", jobId)
                        putExtra("messageId", messageId)
                        data = Uri.parse("smssender://sent/$jobId/$messageId/$i")
                    }
                    val deliveredIntent = Intent(DELIVERED_SMS_ACTION).apply {
                        setClass(this@SmsSenderService, SmsDeliveredReceiver::class.java)
                        putExtra("jobId", jobId)
                        putExtra("messageId", messageId)
                        data = Uri.parse("smssender://delivered/$jobId/$messageId/$i")
                    }

                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
                    sentIntents.add(PendingIntent.getBroadcast(this, sentRequestCode, sentIntent, flags))
                    deliveredIntents.add(PendingIntent.getBroadcast(this, deliveredRequestCode, deliveredIntent, flags))
                }

                try {
                    smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveredIntents)
                } catch (ex: Exception) {
                    Log.e("SmsSenderService", "Błąd podczas wysyłania SMS do $phone", ex)
                    CoroutineScope(Dispatchers.IO).launch {
                        FirestoreUpdateHelper.updateMessageStatus(jobId, messageId, "error_sending_failed")
                    }
                }
            }
        }
        updateNotification("Wysłano ${messages.size} SMS. Oczekiwanie na raporty...")
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlag)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SMS Sender Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SMS Sender Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        syncHandler.removeCallbacks(syncRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
