package com.example.simple_webrtc

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.simple_webrtc.databinding.ActivityMainBinding
import com.example.simple_webrtc.rtc.WebRTCClient
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webRTCClient: WebRTCClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()

        SocketService.setContext(this)

        binding.fab.setOnClickListener { view ->
            webRTCClient = WebRTCClient()
            webRTCClient.setLocalPlayerView(binding.svMe)
            webRTCClient.setNetWorkPlayerView(binding.svAnother)
            webRTCClient.start(false)

        }

        val serviceIntent = Intent(this, MainService::class.java)
        startService(serviceIntent)


    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val newType = intent!!.getStringExtra("EXTRA_TYPE")
        if (newType != null && newType.isNotEmpty()) {
            if (newType.contains("Incoming")) {
                webRTCClient = SocketService.incomingRTCCall ?: return
                webRTCClient.setNetWorkPlayerView(binding.svAnother)
                webRTCClient.start(true)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (!EasyPermissions.hasPermissions(this, *permissions)) {
            // Permission is not granted
            Log.d("checkCameraPermissions", "No Camera Permissions")
            EasyPermissions.requestPermissions(this, "Please provide permissions", 1, *permissions)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.d(ContentValues.TAG, "Permission request successful")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.d(ContentValues.TAG, "Permission request failed")
    }

}