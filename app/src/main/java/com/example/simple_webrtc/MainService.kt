package com.example.simple_webrtc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.rtc.Ping
import com.example.simple_webrtc.utils.Constant
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.Utils
import com.example.simple_webrtc.utils.cache.SPUtils
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class MainService : Service(), Runnable {

    private val binder = MainBinder()
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var contactList: List<Contact> = emptyList()

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

        fun pingContacts(contactList: List<Contact>) {
            Log.d(this, "pingContacts()")
            Thread(
                Ping(binder, contactList)
            ).start()
        }

        fun addContact(targetContact: String) {
            val contactSet = HashSet<String>()
            contactSet.addAll(SPUtils.getInstance().getStringSet(Constant.SERVICE_IP_LIST))
            contactSet.add(targetContact)
            SPUtils.getInstance().put(Constant.SERVICE_IP_LIST, contactSet)
        }

        fun getCurrentIPAddress(): String? {
            return Utils.getIpAddressString()
        }

        private fun getDeviceName(): String {
            return Utils.getDeviceName()
        }

        fun getContactList(): List<Contact> {
            return contactList
        }

        fun refreshContactList(): List<Contact> {
            val contactSet = SPUtils.getInstance().getStringSet(Constant.SERVICE_IP_LIST)
            if (contactSet.size > 0) {
                val dataList = ArrayList<Contact>()
                contactSet.forEach {
                    val contact = Contact(it.substringBefore(","), it.substringAfter(","))
                    dataList.add(contact)
                }
                contactList = dataList
            }
            return contactList
        }

        fun getSelfContact(): Contact {
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

        fun refreshContacts(ctx: Context) {
            LocalBroadcastManager.getInstance(ctx)
                .sendBroadcast(Intent("refresh_contact_list"))
        }
    }
}