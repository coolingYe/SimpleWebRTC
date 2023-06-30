package com.example.simple_webrtc

import android.app.Application
import org.webrtc.PeerConnectionFactory

class WebRTCMain : Application() {

    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
    }
}