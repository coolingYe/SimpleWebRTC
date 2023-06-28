package com.example.simple_webrtc

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import com.example.simple_webrtc.utils.Constant
import com.example.simple_webrtc.utils.Log
import java.io.IOException
import java.net.ServerSocket

class MainService : Service(), Runnable {

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isServerSocketRunning = true

    override fun onCreate() {
        super.onCreate()
        Thread(this).start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun run() {
        try {
            serverSocket = ServerSocket(Constant.SERVER_HTTP_PORT)
            while (isServerSocketRunning) {
                try {
                    val socket = serverSocket!!.accept()
                    Log.d(this, "MainService new incoming connection")
                    SocketService.createIncomingCall(socket)
                } catch (e: IOException) {
                    // ignore
                }
            }
        } catch (e: IOException) {
            Log.e(this, "run() e=$e")
            e.printStackTrace()
            Handler(mainLooper).post { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
        }
    }

    inner class MainBinder : Binder() {
        fun getService(): MainService {
            return this@MainService
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServerSocketRunning = false
    }
}