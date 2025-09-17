package com.example.smssenderservice

import android.content.Context

object CustomTokenProvider {

    private const val PLACEHOLDER_VALUE = ""

    fun getToken(context: Context): String? {
        val token = context.getString(R.string.firebase_custom_token)
        return token.takeIf { it.isNotBlank() && it != PLACEHOLDER_VALUE }
    }
}