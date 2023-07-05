package com.example.simple_webrtc

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturelib.Config
import com.example.gesturelib.GestureManager
import com.example.gesturelib.LocationEnum
import com.example.simple_webrtc.databinding.ActivityCallBinding
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.rtc.WebRTCClient
import org.webrtc.EglBase

class CallActivity : AppCompatActivity(), WebRTCClient.CallContext {

    private lateinit var binding: ActivityCallBinding
    private lateinit var webRTCClient: WebRTCClient
    private lateinit var gestureManager: GestureManager

    private var proxyVideoSink = WebRTCClient.ProxyVideoSink()
    private val eglBase = EglBase.create()
    private var contact: Contact? = null
    private val isGestureStart = true

    companion object {
        const val ACTION_OUTGOING_CALL = "ACTION_OUTGOING_CALL"
        const val ACTION_INCOMING_CALL = "ACTION_INCOMING_CALL"
        const val EXTRA_CONTACT = "EXTRA_CONTACT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        contact = intent.extras!![EXTRA_CONTACT] as Contact
        when (intent.action) {
            ACTION_OUTGOING_CALL -> initCalling(contact!!)
            ACTION_INCOMING_CALL -> initIncoming()
            else -> {
                finish()
            }
        }
        initViews()
        initListener()
    }

    private fun initViews() {
        initGesture()
    }

    private fun initListener() {
        binding.btnDataChannelSend.setOnClickListener {
            if (webRTCClient != null) {
                webRTCClient.sendOnDataChannel("DataChannel Send Test.")
            }
        }

        proxyVideoSink.setCameraCallback {
            if (it != null && isGestureStart) {
                gestureManager.method(it)
            }
        }
    }

    private fun initCalling(contact: Contact) {
        webRTCClient = WebRTCClient(contact)
        webRTCClient.setCallContext(this)
        binding.videoPlayer.init(eglBase.eglBaseContext, null)
        webRTCClient.setPlayerView(binding.videoPlayer)
        webRTCClient.setProxyVideoSink(proxyVideoSink)
        webRTCClient.setEglBase(eglBase)
        webRTCClient.start(false)
    }

    private fun initIncoming() {
        webRTCClient = SocketService.incomingRTCCall ?: return
        webRTCClient.setCallContext(this)
        binding.videoPlayer.init(eglBase.eglBaseContext, null)
        webRTCClient.setPlayerView(binding.videoPlayer)
        webRTCClient.setProxyVideoSink(proxyVideoSink)
        webRTCClient.setEglBase(eglBase)
        webRTCClient.start(true)
        proxyVideoSink.setTarget(binding.videoPlayer)
    }

    private fun initGesture() {
        val authDetails = ArrayList<String>()
        authDetails.add(Config.ak)
        authDetails.add(Config.sk)
        authDetails.add(Config.url)
        gestureManager = GestureManager.getInstance(this)
        binding.overlayView.initOverlay(LocationEnum.PERSONPOSE)
        gestureManager.init(authDetails, binding.overlayView)
        gestureManager.onCameraOpened()
    }

    override fun onDataChannelCallback(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.overlayView.release()
        gestureManager.releaseAllZee()
        webRTCClient?.let {
            it.destroy()
        }
    }
}