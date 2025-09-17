package com.example.smssenderservice // Zmień na swoją nazwę pakietu

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsDeliveryReceiver", "Odebrano komunikat: ${intent.action} z kodem: $resultCode")

        // Przygotuj nowy intent, który zostanie wysłany do naszej usługi w tle
        val serviceIntent = Intent(context, SmsSenderService::class.java)
        // Skopiuj wszystkie dane z oryginalnego komunikatu
        serviceIntent.putExtras(intent.extras ?: return)
        // Dodaj kod rezultatu (np. czy dostarczono pomyślnie)
        serviceIntent.putExtra("resultCode", resultCode)
        // Ustaw akcję, aby usługa wiedziała, co ma zrobić
        serviceIntent.action = intent.action

        // Uruchom usługę, aby przetworzyła ten raport
        context.startService(serviceIntent)
    }
}
