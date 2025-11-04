package com.example.smssenderservice

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PREFS_NAME = "sent_messages_repository"
private const val KEY_MESSAGES = "messages"
const val ACTION_MESSAGE_LOG_UPDATED = "com.example.smssenderservice.MESSAGE_LOG_UPDATED"

object SentMessagesRepository {

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun recordSendingAttempt(context: Context, info: SentMessageInfo) {
        val current = loadMessages(context).toMutableList()
        current.removeAll { it.messageId == info.messageId }
        current.add(info)
        saveMessages(context, current)
    }

    fun updateStatus(context: Context, messageId: String, status: MessageSendStatus) {
        val current = loadMessages(context).toMutableList()
        val index = current.indexOfFirst { it.messageId == messageId }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(status = status)
            saveMessages(context, current)
        }
    }

    fun getMessages(context: Context): List<SentMessageInfo> {
        return loadMessages(context)
            .sortedByDescending { it.timestamp }
    }

    fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    @Synchronized
    private fun saveMessages(context: Context, items: List<SentMessageInfo>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val limited = items.sortedByDescending { it.timestamp }.take(200)
        val jsonArray = JSONArray()
        limited.forEach { info ->
            val obj = JSONObject()
            obj.put("messageId", info.messageId)
            obj.put("phoneNumber", info.phoneNumber)
            obj.put("messageBody", info.messageBody)
            obj.put("timestamp", info.timestamp)
            obj.put("status", info.status.name)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES, jsonArray.toString()).apply()
        notifyObservers(context)
    }

    @Synchronized
    private fun loadMessages(context: Context): List<SentMessageInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        val jsonArray = JSONArray(jsonString)
        val result = mutableListOf<SentMessageInfo>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val status = runCatching {
                MessageSendStatus.valueOf(obj.getString("status"))
            }.getOrDefault(MessageSendStatus.PENDING)
            result.add(
                SentMessageInfo(
                    messageId = obj.getString("messageId"),
                    phoneNumber = obj.optString("phoneNumber"),
                    messageBody = obj.optString("messageBody"),
                    timestamp = obj.optLong("timestamp"),
                    status = status
                )
            )
        }
        return result
    }

    private fun notifyObservers(context: Context) {
        val intent = Intent(ACTION_MESSAGE_LOG_UPDATED).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
