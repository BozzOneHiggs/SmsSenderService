package com.example.smssenderservice

import android.content.Context

enum class MessageSendStatus(val labelRes: Int, val remoteValue: String) {
    PENDING(R.string.status_pending_label, "pending"),
    SENT(R.string.status_sent_label, "sent"),
    DELIVERED(R.string.status_delivered_label, "delivered"),
    ERROR(R.string.status_error_label, "error");

    fun label(context: Context): String = context.getString(labelRes)
}
