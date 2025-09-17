package com.example.smssenderservice

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {

    private const val TAG = "FirebaseAuthManager"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    suspend fun ensureSignedInWithCustomToken(context: Context): FirebaseFirestore {
        auth.currentUser?.let {
            return FirebaseFirestore.getInstance()
        }

        val customToken = CustomTokenProvider.getToken(context)
            ?: throw IllegalStateException("Brak skonfigurowanego custom tokenu Firebase.")

        return try {
            auth.signInWithCustomToken(customToken).await()
            Log.d(TAG, "Pomyślnie uwierzytelniono za pomocą custom tokenu.")
            FirebaseFirestore.getInstance()
        } catch (ex: Exception) {
            Log.e(TAG, "Nie udało się uwierzytelnić za pomocą custom tokenu.", ex)
            throw ex
        }
    }
}