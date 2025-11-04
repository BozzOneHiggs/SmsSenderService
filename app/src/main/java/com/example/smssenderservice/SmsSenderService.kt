package com.example.smssenderservice // Zmień na swoją nazwę pakietu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SmsSenderService : Service() {

    private lateinit var db: FirebaseFirestore
    private var firestoreListener: ListenerRegistration? = null
    private val NOTIFICATION_CHANNEL_ID = "SmsSenderServiceChannel"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Nasłuchiwanie na nowe zlecenia SMS...")
        startForeground(1, notification)
        serviceScope.launch {
            authenticateAndStartWork()
        }
        return START_STICKY
    }

    private fun listenForSmsJobs() {
        if (!::db.isInitialized) {
            Log.w("SmsSenderService", "Firestore nie jest gotowy do nasłuchiwania zleceń.")
            return
        }
        if (firestoreListener != null) return
        updateNotification("Oczekuję na nowe zlecenia...")
        val query = db.collection("smsJobs")
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(1)

        firestoreListener = query.addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null || snapshots.isEmpty) {
                updateNotification("Oczekuję na nowe zlecenia...")
                return@addSnapshotListener
            }
            val jobDocument = snapshots.documents[0]
            val jobId = jobDocument.id
            updateNotification("Przetwarzanie zlecenia: $jobId")

            db.collection("smsJobs").document(jobId).update("status", "processing")
                .addOnSuccessListener {
                    prepareAndSendMessages(jobId, jobDocument.get("messages") as? List<Map<String, String>>)
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
                "person" to msg["person"],
                "caseNumber" to msg["caseNumber"],
                "status" to "sent" // Ustawiamy od razu status "Wysłano"
            )
        }
        db.collection("smsJobs").document(jobId).update("messages", updatedMessages)
            .addOnSuccessListener {
                sendAllSms(jobId, updatedMessages)
            }
    }

    private fun sendAllSms(jobId: String, messages: List<Map<String, String?>>) {
        val smsManager = this.getSystemService(SmsManager::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        for (msg in messages) {
            val phone = msg["phone"] as? String
            val messageId = msg["id"] as? String ?: continue
            var messageContent = msg["message"] as? String

            if (phone != null && messageContent != null) {
                messageContent = messageContent.replace("&", "\n")
                try {
                    val parts = smsManager.divideMessage(messageContent)
                    val sentIntents = ArrayList<PendingIntent>(parts.size)
                    val deliveredIntents = ArrayList<PendingIntent>(parts.size)

                    for (index in parts.indices) {
                        val sentIntent = Intent(ACTION_SMS_SENT).apply {
                            setPackage(packageName)
                            putExtra("jobId", jobId)
                            putExtra("messageId", messageId)
                        }
                        val deliveredIntent = Intent(ACTION_SMS_DELIVERED).apply {
                            setPackage(packageName)
                            putExtra("jobId", jobId)
                            putExtra("messageId", messageId)
                        }

                        sentIntents.add(
                            PendingIntent.getBroadcast(
                                this,
                                abs("${messageId}_sent_$index".hashCode()),
                                sentIntent,
                                pendingIntentFlags
                            )
                        )
                        deliveredIntents.add(
                            PendingIntent.getBroadcast(
                                this,
                                abs("${messageId}_delivered_$index".hashCode()),
                                deliveredIntent,
                                pendingIntentFlags
                            )
                        )
                    }

                    smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveredIntents)
                    val info = SentMessageInfo(
                        messageId = messageId,
                        phoneNumber = phone,
                        messageBody = messageContent,
                        timestamp = System.currentTimeMillis(),
                        status = MessageSendStatus.PENDING
                    )
                    SentMessagesRepository.recordSendingAttempt(applicationContext, info)
                    Log.d("SmsSenderService", "Wysłano SMS ${messageId} do $phone z raportami dostarczenia")
                } catch (ex: Exception) {
                    Log.e("SmsSenderService", "Błąd podczas wysyłania SMS do $phone", ex)
                    val info = SentMessageInfo(
                        messageId = messageId,
                        phoneNumber = phone ?: "",
                        messageBody = messageContent ?: "",
                        timestamp = System.currentTimeMillis(),
                        status = MessageSendStatus.ERROR
                    )
                    SentMessagesRepository.recordSendingAttempt(applicationContext, info)
                }
            }
        }
        updateNotification("Wysłano ${messages.size} SMS. Statusy zaktualizują się w tle.")
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
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private suspend fun authenticateAndStartWork() {
        try {
            val firestore = withContext(Dispatchers.IO) {
                FirebaseAuthManager.ensureSignedInWithCustomToken(this@SmsSenderService)
            }
            if (!::db.isInitialized) {
                db = firestore
            }
            listenForSmsJobs()
        } catch (ex: Exception) {
            Log.e("SmsSenderService", "Błąd autoryzacji Firebase", ex)
            updateNotification(getString(R.string.auth_error_message))
        }
    }
}
