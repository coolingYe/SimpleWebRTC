package com.example.simple_webrtc

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.example.simple_webrtc.rtc.WebRTCClient
import com.example.simple_webrtc.utils.Constant
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.PacketReader
import com.example.simple_webrtc.utils.PacketWriter
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

abstract class SocketService(
    protected var commSocket: Socket?
) {
    private val executor = Executors.newSingleThreadExecutor()

    abstract fun handleAnswer(remoteDesc: String)

    protected fun cleanupRTCPeerConnection() {
        execute {
            Log.d(this, "cleanup() executor start")
            try {
                Log.d(this, "cleanup() close socket")
                commSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Log.d(this, "cleanup() executor end")
        }

        // wait for tasks to finish
        executor.shutdown()
        executor.awaitTermination(4L, TimeUnit.SECONDS)
    }

    fun createOutgoingCall(offer: String) {
        Log.d(this, "createOutgoingCall()")
        Thread {
            try {
                createOutgoingCallInternal(offer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun createOutgoingCallInternal(offer: String) {
        val socket = connectSocket()
        commSocket = socket
        socket?.let {
            val pr = PacketReader(socket)
            val pw = PacketWriter(socket)

            val obj = JSONObject()
            obj.put("action", "call")
            obj.put("offer", offer)
            pw.writeMessage(obj.toString().toByteArray())

            while (!socket.isClosed) {
                val response = pr.readMessage()
                if (response == null) {
                    Thread.sleep(SOCKET_TIMEOUT_MS / 10)
                    Log.d(this, "createOutgoingCallInternal() response is null")
                    continue
                }

                val message = String((response), Charsets.UTF_8)
                Log.d(this, "createOutgoingCallInternal() ---> response : $message")
                val obj1 = JSONObject(message)
                val action = obj1.getString("action")
                if (action == "connected") {
                    val answer = obj1.getString("answer")
                    handleAnswer(answer)
                }
            }
        } ?: return
    }

    private fun connectSocket(): Socket? {
        val socket = Socket()
        socket.connect(parseSocketAddress(Constant.SERVER_HTTP), connectTimeout)
        return socket ?: null
    }

    private fun parseSocketAddress(addressStr: String): SocketAddress? {
        val parts = addressStr.split(":".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (parts.size != 2) {
            System.err.println("Invalid address format: $addressStr")
            return null
        }
        val host = parts[0]
        val port: Int = try {
            parts[1].toInt()
        } catch (e: NumberFormatException) {
            System.err.println("Invalid port number: " + parts[1])
            return null
        }
        return InetSocketAddress(host, port)
    }

    protected fun execute(r: Runnable) {
        try {
            executor.execute(r)
        } catch (e: RejectedExecutionException) {
            e.printStackTrace()
            // can happen when the executor has shut down
            Log.w(this, "execute() catched RejectedExecutionException")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(this, "execute() catched $e")
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 3000L

        const val connectTimeout = 500

        @SuppressLint("StaticFieldLeak")
        var incomingRTCCall: WebRTCClient? = null

        @SuppressLint("StaticFieldLeak")
        var contextMain: Context? = null

        fun setContext(context: Context) {
            this.contextMain = context
        }

        fun closeSocket(socket: Socket?) {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createIncomingCall(socket: Socket) {
            Thread {
                try {
                    createIncomingCallInternal(socket)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //decline()
                }
            }.start()
        }

        private fun createIncomingCallInternal(socket: Socket) {
            val pw = PacketWriter(socket)
            val pr = PacketReader(socket)

            val request = pr.readMessage()
            if (request == null) {
                Log.d(this, "createIncomingCallInternal() connection closed")
                socket.close()
                return
            }

            val message = String((request), Charsets.UTF_8)
            val obj = JSONObject(message)
            val action = obj.optString("action", "")
            Log.d(this, "createIncomingCallInternal() action: $action")
            when (action) {
                "call" -> {
//                    val callback = "{\"action\":\"ringing\"}"
//                    pw.writeMessage(callback.toByteArray(Charsets.UTF_8))

                    val offer = obj.getString("offer")
                    Log.d(this, "Dior: ------> $offer");

                    incomingRTCCall?.cleanup()
                    incomingRTCCall = WebRTCClient(socket, offer)

                    try {
                        Looper.prepare()
                        val messageDialog = AlertDialog.Builder(contextMain)
                        with(messageDialog) {
                            setTitle("Call Incoming")
                            setMessage("Do you agree to accept?")
                            setPositiveButton("Accept") { dialog, _ ->
                                run {
                                    val intent = Intent(contextMain, MainActivity::class.java)
                                    intent.putExtra("EXTRA_TYPE", "Incoming")
                                    contextMain?.startActivity(intent)
                                    dialog.dismiss()
                                }
                            }
                            setNegativeButton("Cancel") { dialog, _ ->
                                run {
                                    dialog.dismiss()
                                }
                            }
                        }
                        messageDialog.show()
                        Looper.loop()
                    } catch (e: Exception) {
                        incomingRTCCall?.cleanup()
                        incomingRTCCall = null
                        e.printStackTrace()
                    }
                }
                else -> {
                    Log.d(this, "createIncomingCallInternal(): ------> $action");
                }
            }
        }
    }
}