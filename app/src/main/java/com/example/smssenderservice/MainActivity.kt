package com.example.smssenderservice // Zmień na swoją nazwę pakietu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private lateinit var statusTextView: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        actionButton = findViewById(R.id.syncButton)
    }

    override fun onResume() {
        super.onResume()
        updateUiBasedOnPermissions()
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun allPermissionsGranted(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateUiBasedOnPermissions() {
        if (allPermissionsGranted()) {
            statusTextView.text = "Wszystkie uprawnienia nadane. Usługa w tle jest aktywna."
            actionButton.text = "Synchronizuj statusy ręcznie"
            actionButton.setOnClickListener {
                statusTextView.text = "Rozpoczynam ręczną synchronizację..."
                actionButton.isEnabled = false
                CoroutineScope(Dispatchers.IO).launch {
                    SmsStatusSyncer.sync(this@MainActivity) { updatedCount ->
                        // Wróć do głównego wątku, aby zaktualizować UI
                        launch(Dispatchers.Main) {
                            statusTextView.text = "Synchronizacja zakończona. Zaktualizowano $updatedCount statusów."
                            actionButton.isEnabled = true
                        }
                    }
                }
            }
            startSmsService()
        } else {
            statusTextView.text = "Aplikacja wymaga dodatkowych uprawnień do działania."
            actionButton.text = "Nadaj uprawnienia"
            actionButton.setOnClickListener { checkPermissionsAndStartService() }
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_CODE)
        } else {
            startSmsService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startSmsService()
            } else {
                if (permissions.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) && ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                    showManualPermissionGuidance()
                }
            }
        }
        updateUiBasedOnPermissions()
    }

    private fun showManualPermissionGuidance() {
        statusTextView.text = "Niektóre uprawnienia zostały trwale odrzucone. Musisz je nadać ręcznie w ustawieniach aplikacji."
        actionButton.text = "Otwórz ustawienia aplikacji"
        actionButton.setOnClickListener { openAppSettings() }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun startSmsService() {
        val serviceIntent = Intent(this, SmsSenderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Usługa SMS w tle jest aktywna", Toast.LENGTH_SHORT).show()
    }
}
