package com.example.smssenderservice

data class SentMessageInfo(
    val messageId: String,
    val phoneNumber: String,
    val messageBody: String,
    val timestamp: Long,
    val status: MessageSendStatus
)
