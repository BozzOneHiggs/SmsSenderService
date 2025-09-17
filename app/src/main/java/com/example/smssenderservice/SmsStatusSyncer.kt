package com.example.smssenderservice // Zmień na swoją nazwę pakietu

import android.content.Context
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object SmsStatusSyncer {

    fun sync(context: Context, db: FirebaseFirestore, callback: (Int) -> Unit) {
        Log.d("SmsStatusSyncer", "Rozpoczynam synchronizację statusów...")
        var updatedCount = 0

        db.collection("smsJobs")
            .whereEqualTo("status", "processing")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("SmsStatusSyncer", "Brak aktywnych zleceń do synchronizacji.")
                    callback(0)
                    return@addOnSuccessListener
                }

                val phoneDb = readPhoneSmsDatabase(context)
                if (phoneDb.isEmpty()) {
                    Log.d("SmsStatusSyncer", "Baza danych SMS w telefonie jest pusta lub niedostępna.")
                    callback(0)
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val jobId = doc.id
                    val messages = doc.get("messages") as? List<HashMap<String, Any>> ?: continue

                    messages.forEach { msg ->
                        val phone = msg["phone"] as? String
                        val messageId = msg["id"] as? String
                        val currentStatus = msg["status"] as? String

                        if (phone != null && messageId != null && currentStatus != "delivered") {
                            if (isDeliveredInPhoneDb(phone, phoneDb)) {
                                val updates = mapOf(
                                    "status" to "delivered",
                                    "deliveredAt" to FieldValue.serverTimestamp()
                                )
                                updateMessageStatus(db, jobId, messageId, updates)
                                updatedCount++
                            }
                        }
                    }
                }
                Log.d("SmsStatusSyncer", "Synchronizacja zakończona. Zaktualizowano $updatedCount statusów.")
                callback(updatedCount)
            }
            .addOnFailureListener { e ->
                Log.e("SmsStatusSyncer", "Błąd podczas synchronizacji", e)
                callback(0)
            }
    }

    private fun readPhoneSmsDatabase(context: Context): Map<String, Int> {
        val sentSmsMap = mutableMapOf<String, Int>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.STATUS)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Sent.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 200"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val status = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.STATUS))
                // Zapisujemy tylko najnowszy status dla danego numeru
                if (address != null && !sentSmsMap.containsKey(address)) {
                    sentSmsMap[address] = status
                }
            }
        }
        return sentSmsMap
    }

    private fun isDeliveredInPhoneDb(phoneNumber: String, phoneDb: Map<String, Int>): Boolean {
        for ((address, status) in phoneDb) {
            if (PhoneNumberUtils.compare(phoneNumber, address)) {
                return status == Telephony.Sms.STATUS_COMPLETE
            }
        }
        return false
    }

    private fun updateMessageStatus(db: FirebaseFirestore, jobId: String, messageId: String, updates: Map<String, Any>) {
        val jobRef = db.collection("smsJobs").document(jobId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(jobRef)
            val messages = snapshot.get("messages") as? List<HashMap<String, Any>>
            if (messages != null) {
                val updatedMessages = messages.map { msg ->
                    if (msg["id"] == messageId) {
                        msg.toMutableMap().apply { putAll(updates) }.toMap()
                    } else {
                        msg
                    }
                }
                transaction.update(jobRef, "messages", updatedMessages)
            }
            null
        }
    }
}
