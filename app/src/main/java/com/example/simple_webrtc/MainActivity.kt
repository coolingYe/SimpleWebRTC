package com.example.simple_webrtc

import android.Manifest
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.simple_webrtc.databinding.ActivityMainBinding
import com.example.simple_webrtc.rtc.WebRTCClient
import org.webrtc.EglBase
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks, WebRTCClient.CallContext {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webRTCClient: WebRTCClient
    private var proxyVideoSink = WebRTCClient.ProxyVideoSink()
    private val eglBase = EglBase.create()

    private var binder: MainService.MainBinder? = null
    private lateinit var connection: ServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkPermissions()

        MainService.start(this)

        SocketService.setContext(this)
        proxyVideoSink.setTarget(binding.videoPlayer)

        binding.btnCall.setOnClickListener { view ->
            webRTCClient = WebRTCClient()
            webRTCClient.setCallContext(this@MainActivity)
            binding.videoPlayer.init(eglBase.eglBaseContext, null)
            webRTCClient.setPlayerView(binding.videoPlayer)
            webRTCClient.setProxyVideoSink(proxyVideoSink)
            webRTCClient.setEglBase(eglBase)
            webRTCClient.start(false)
        }

        binding.btnCamera.setOnClickListener {
            webRTCClient.setCameraEnabled(true)
        }

        binding.btnDataChannelSend.setOnClickListener{
            if (webRTCClient != null) {
                webRTCClient.sendOnDataChannel("DataChannel Send Test.")
            }
        }

        binding.btnSocketSend.setOnClickListener {
            if (webRTCClient != null) {
                webRTCClient.sendOnSocket("DataChannel Send Test.")
            }
        }

        binding.btnRelease.setOnClickListener {
            webRTCClient.destroy()
            MainService.stop(this)
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service as MainService.MainBinder
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }

        bindService(Intent(this, MainService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val newType = intent!!.getStringExtra("EXTRA_TYPE")
        if (newType != null && newType.isNotEmpty()) {
            if (newType.contains("Incoming")) {
                webRTCClient = SocketService.incomingRTCCall ?: return
                webRTCClient.setCallContext(this@MainActivity)
                binding.videoPlayer.init(eglBase.eglBaseContext, null)
                webRTCClient.setPlayerView(binding.videoPlayer)
                webRTCClient.setProxyVideoSink(proxyVideoSink)
                webRTCClient.setEglBase(eglBase)
                webRTCClient.start(true)
                proxyVideoSink.setTarget(binding.videoPlayer)
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

    override fun onDestroy() {
        super.onDestroy()
        webRTCClient.destroy()
    }

    override fun onDataChannelCallback(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

}