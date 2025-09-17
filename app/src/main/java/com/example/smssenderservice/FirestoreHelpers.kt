package com.example.smssenderservice

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirestoreUpdateHelper {
    private val db = FirebaseFirestore.getInstance()

    suspend fun updateMessageStatus(jobId: String, messageId: String, newStatus: String, isFinalStatus: Boolean = false) {
        try {
            val jobRef = db.collection("smsJobs").document(jobId)
            val jobSnapshot = jobRef.get().await()
            val messages = (jobSnapshot.get("messages") as? List<Map<String, Any>>)?.toMutableList()

            if (messages != null) {
                val messageIndex = messages.indexOfFirst { it["id"] == messageId }
                if (messageIndex != -1) {
                    val currentStatus = messages[messageIndex]["status"] as? String
                    if (currentStatus == "delivered" && newStatus == "sent") {
                        Log.d("FirestoreUpdateHelper", "Ignorowanie statusu 'sent', wiadomość $messageId jest już 'delivered'.")
                        return
                    }

                    val updatedMessage = messages[messageIndex].toMutableMap()
                    updatedMessage["status"] = newStatus
                    if (isFinalStatus) {
                        updatedMessage["deliveredAt"] = FieldValue.serverTimestamp()
                    }
                    messages[messageIndex] = updatedMessage
                    jobRef.update("messages", messages).await()
                    Log.d("FirestoreUpdateHelper", "Zaktualizowano status wiadomości $messageId na $newStatus")

                    val allDone = messages.all { it["status"] == "delivered" || it["status"].toString().contains("error") }
                    if (allDone) {
                        jobRef.update("status", "completed").await()
                        Log.d("FirestoreUpdateHelper", "Wszystkie wiadomości w zleceniu $jobId zakończone.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreUpdateHelper", "Błąd podczas aktualizacji statusu wiadomości $messageId", e)
        }
    }
}
