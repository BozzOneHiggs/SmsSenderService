package com.example.smssenderservice // Zmień na swoją nazwę pakietu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private lateinit var statusTextView: TextView
    private lateinit var permissionButton: com.google.android.material.button.MaterialButton
    private lateinit var emptyStateTextView: TextView
    private lateinit var previewTitle: TextView
    private lateinit var previewTimestamp: TextView
    private lateinit var previewContent: TextView
    private lateinit var messagesAdapter: SentMessageAdapter
    private var logReceiverRegistered = false

    private val logUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshMessageList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        permissionButton = findViewById(R.id.permissionButton)
        emptyStateTextView = findViewById(R.id.emptyStateTextView)
        previewTitle = findViewById(R.id.messagePreviewTitle)
        previewTimestamp = findViewById(R.id.messagePreviewTimestamp)
        previewContent = findViewById(R.id.messagePreviewContent)

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sentMessagesRecyclerView)
        messagesAdapter = SentMessageAdapter { messageInfo ->
            showMessagePreview(messageInfo)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messagesAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        permissionButton.setOnClickListener { checkPermissionsAndStartService() }

        refreshMessageList()
        clearMessagePreview()
    }

    override fun onResume() {
        super.onResume()
        updateUiBasedOnPermissions()
        registerLogReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterLogReceiver()
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
            statusTextView.text = getString(R.string.permissions_granted_message)
            permissionButton.visibility = android.view.View.GONE
            startSmsService()
        } else {
            statusTextView.text = getString(R.string.permissions_missing_message)
            permissionButton.apply {
                visibility = android.view.View.VISIBLE
                isEnabled = true
                text = getString(R.string.grant_permissions_button)
            }
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
        statusTextView.text = getString(R.string.manual_permission_guidance)
        permissionButton.apply {
            visibility = android.view.View.VISIBLE
            text = getString(R.string.open_app_settings_button)
            setOnClickListener { openAppSettings() }
        }
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

    private fun refreshMessageList() {
        val messages = SentMessagesRepository.getMessages(this)
        messagesAdapter.submitList(messages)
        if (messages.isEmpty()) {
            emptyStateTextView.visibility = android.view.View.VISIBLE
            clearMessagePreview()
        } else {
            emptyStateTextView.visibility = android.view.View.GONE
        }
    }

    private fun showMessagePreview(messageInfo: SentMessageInfo) {
        previewTitle.text = getString(R.string.message_preview_for_number, messageInfo.phoneNumber)
        previewTimestamp.text = SentMessagesRepository.formatTimestamp(messageInfo.timestamp)
        previewContent.text = messageInfo.messageBody
    }

    private fun clearMessagePreview() {
        previewTitle.text = getString(R.string.message_preview_title)
        previewTimestamp.text = ""
        previewContent.text = getString(R.string.message_preview_placeholder)
    }

    private fun registerLogReceiver() {
        if (!logReceiverRegistered) {
            val filter = IntentFilter(ACTION_MESSAGE_LOG_UPDATED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(logUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(logUpdatedReceiver, filter)
            }
            logReceiverRegistered = true
        }
    }

    private fun unregisterLogReceiver() {
        if (logReceiverRegistered) {
            unregisterReceiver(logUpdatedReceiver)
            logReceiverRegistered = false
        }
    }
}
