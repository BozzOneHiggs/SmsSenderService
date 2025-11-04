package com.example.smssenderservice

import android.content.Context

enum class MessageSendStatus(val labelRes: Int) {
    PENDING(R.string.status_pending_label),
    SENT(R.string.status_sent_label),
    ERROR(R.string.status_error_label);

    fun label(context: Context): String = context.getString(labelRes)
}
