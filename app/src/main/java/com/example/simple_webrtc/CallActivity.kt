package com.example.simple_webrtc

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.gesturelib.Config
import com.example.gesturelib.GestureManager
import com.example.gesturelib.LocationEnum
import com.example.simple_webrtc.SocketService.CallState
import com.example.simple_webrtc.databinding.ActivityCallBinding
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.rtc.WebRTCClient
import com.example.simple_webrtc.utils.Log
import org.json.JSONObject
import org.webrtc.EglBase

class CallActivity : AppCompatActivity(), WebRTCClient.CallContext {

    private lateinit var binding: ActivityCallBinding
    private lateinit var gestureManager: GestureManager
    private lateinit var connection: ServiceConnection
    private var binder: MainService.MainBinder? = null

    private var webRTCClient: WebRTCClient? = null
    private var proxyVideoSink = WebRTCClient.ProxyVideoSink()
    private val eglBase = EglBase.create()
    private var contact: Contact? = null
    private var isGestureStart = false
    private var activityActive = true

    companion object {
        const val ACTION_OUTGOING_CALL = "ACTION_OUTGOING_CALL"
        const val ACTION_INCOMING_CALL = "ACTION_INCOMING_CALL"
        const val EXTRA_CONTACT = "EXTRA_CONTACT"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        when (intent.action) {
            ACTION_OUTGOING_CALL -> {
                contact = intent.extras!![EXTRA_CONTACT] as Contact
                initCalling(contact!!)
            }
            ACTION_INCOMING_CALL -> initIncoming()
            else -> {
                finish()
            }
        }
        initListener()
    }

    private fun initListener() {
        binding.edMessage.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                if (v.text.isNotEmpty()) {
                    webRTCClient?.let {
                        val json = JSONObject().apply {
                            put(WebRTCClient.ACTION_TYPE, WebRTCClient.MESSAGE)
                            put(WebRTCClient.MESSAGE, "${v.text}")
                        }
                        if (it.sendOnDataChannel(json.toString())) {
                            v.text = ""
                        }

                    }
                }
            }
            true
        }

        binding.btnClose.setOnClickListener {
            webRTCClient?.hangup()
        }

        binding.btnSwitchCamera.setOnClickListener {
            webRTCClient?.switchCamera()
        }

        binding.btnGraphics.setOnClickListener {
            isGestureStart = !isGestureStart
            binding.overlayView.isVisible = isGestureStart
            proxyVideoSink.setIsGestureStart(isGestureStart)
            if (isGestureStart) {
                initGesture()
            } else {
                binding.overlayView.release()
                gestureManager.releaseAllZee()
            }
        }

        proxyVideoSink.setCameraCallback { bitmap ->
            if (bitmap != null && isGestureStart) {
                gestureManager.method(bitmap)
            }
        }
    }

    private fun initCalling(contact: Contact) {
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service as MainService.MainBinder
                webRTCClient = WebRTCClient(contact)
                binding.videoPlayer.init(eglBase.eglBaseContext, null)

                with(webRTCClient) {
                    this?.let {
                        setCallContext(this@CallActivity)
                        setPlayerView(binding.videoPlayer)
                        setEglBase(eglBase)
                        setProxyVideoSink(proxyVideoSink)
                        start(false)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }

        bindService(Intent(this, MainService::class.java), connection, 0)
    }

    private fun initIncoming() {
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service as MainService.MainBinder
                webRTCClient = SocketService.incomingRTCCall ?: run {
                    finish()
                    return
                }
                binding.videoPlayer.init(eglBase.eglBaseContext, null)
                with(webRTCClient) {
                    this?.let {
                        setCallContext(this@CallActivity)
                        setPlayerView(binding.videoPlayer)
                        setEglBase(eglBase)
                        setProxyVideoSink(proxyVideoSink)
                        start(true)
                    }
                }
                proxyVideoSink.setTarget(binding.videoPlayer)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }

        }

        bindService(Intent(this, MainService::class.java), connection, 0)
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
            Toast.makeText(this, "${contact?.name}: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStateChange(state: CallState) {
        runOnUiThread {
            when (state) {
                CallState.CONNECTED -> {}
                CallState.DISMISSED, CallState.ENDED -> {
                    finishDelayed()
                }
                CallState.ERROR_COMMUNICATION -> errorAction(CallState.ERROR_COMMUNICATION.toString())
                CallState.ERROR_UNKNOWN_HOST -> errorAction(CallState.ERROR_UNKNOWN_HOST.toString())
                CallState.ERROR_NO_CONNECTION -> errorAction(CallState.ERROR_NO_CONNECTION.toString())
                CallState.ERROR_NO_ADDRESSES -> errorAction(CallState.ERROR_NO_ADDRESSES.toString())
                CallState.ERROR_CONNECT_PORT -> errorAction(CallState.ERROR_CONNECT_PORT.toString())
                CallState.ERROR_NO_NETWORK -> errorAction(CallState.ERROR_NO_NETWORK.toString())
                else -> {}
            }
        }
    }

    private fun finishDelayed() {
        if (activityActive) {
            activityActive = false
            Handler(mainLooper).postDelayed({ finish() }, 500)
        }
    }

    private fun errorAction(hint: String) {
        finishDelayed()
        showToast(hint)
    }

    private fun showToast(hint: String) {
        Toast.makeText(this@CallActivity, hint, Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isGestureStart) {
                binding.overlayView.release()
                gestureManager.releaseAllZee()
            }

            webRTCClient?.cleanup()

            unbindService(connection)

            proxyVideoSink.setTarget(null)
            binding.videoPlayer.release()

            webRTCClient?.releaseCamera()
            eglBase.release()
        } catch (e: Exception) {
            Log.e(this, "onDestroy() error = $e")
        } finally {
            SocketService.incomingRTCCall = null
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webRTCClient?.hangup()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}