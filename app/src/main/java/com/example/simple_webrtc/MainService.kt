package com.example.simple_webrtc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.utils.Constant
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.Utils
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class MainService : Service(), Runnable {

    private val binder = MainBinder()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    @Volatile
    private var isServerSocketRunning = true

    override fun onCreate() {
        super.onCreate()
        Thread(this).start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun run() {
        try {
            serverSocket = ServerSocket(Constant.SERVER_HTTP_PORT)
            while (isServerSocketRunning) {
                try {
                    serverSocket?.let { server ->
                        socket = server.accept()
                        socket?.let { socket ->
                            Log.d(this, "MainService new incoming connection")
                            SocketService.createIncomingCall(binder, socket)
                        }
                    }
                } catch (e: IOException) {
                    Log.d(this, "  open ----->e$e")
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

        fun getSocket(): Socket? {
            return socket
        }

        fun getCurrentIPAddress(): String? {
            return Utils.getIpAddressString()
        }

        fun getDeviceName(): String {
            return Utils.getDeviceName()
        }

        fun getContact(): Contact {
            return Contact(getDeviceName(), getCurrentIPAddress())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServerSocketRunning = false
    }

    companion object {

        fun start(ctx: Context) {
            val startIntent = Intent(ctx, MainService::class.java)
            ctx.startService(startIntent)
        }

        fun stop(ctx: Context) {
            val stopIntent = Intent(ctx, MainService::class.java)
            ctx.stopService(stopIntent)
        }
    }
}