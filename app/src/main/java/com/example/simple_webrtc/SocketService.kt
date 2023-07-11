package com.example.simple_webrtc

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.rtc.WebRTCClient
import com.example.simple_webrtc.utils.*
import com.example.simple_webrtc.utils.Utils.parseSocketAddress
import org.json.JSONObject
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

abstract class SocketService(
    protected var commSocket: Socket?
) {
    private val executor = Executors.newSingleThreadExecutor()

    abstract fun handleAnswer(remoteDesc: String)
    abstract fun reportStateChange(state: CallState)

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

    fun sendOnSocket(message: String) {
        Thread {
            commSocket?.let { socket ->
                val pw = PacketWriter(socket)
                pw.writeMessage(message.toByteArray())
            }
        }.start()
    }

    fun createOutgoingCall(contact: Contact, offer: String) {
        Log.d(this, "createOutgoingCall()")
        Thread {
            try {
                createOutgoingCallInternal(contact, offer)
            } catch (e: Exception) {
                Log.d(this, "createOutgoingCall err ---> $e")
                e.printStackTrace()
            }
        }.start()
    }

    private fun createOutgoingCallInternal(contact: Contact, offer: String) {
        val socket = connectSocket(contact)
        commSocket = socket
        socket?.let {
            val pr = PacketReader(socket)
            val pw = PacketWriter(socket)

            val obj = JSONObject()
            obj.put("action", "call")
            obj.put("offer", offer)
            pw.writeMessage(obj.toString().toByteArray())

            val response = pr.readMessage()
            if (response == null) {
                Thread.sleep(SOCKET_TIMEOUT_MS / 10)
                Log.d(this, "createOutgoingCallInternal() response is null")
                return
            }
            val message = String((response), Charsets.UTF_8)
            val obj1 = JSONObject(message)
            val action = obj1.getString("action")

            Log.d(this, "createOutgoingCallInternal() ---> response : $message")

            while (!socket.isClosed) {
                when (action) {
                    "connected" -> {
                        val answer = obj1.getString("answer")
                        handleAnswer(answer)
                        break
                    }
                    "dismissed" -> {
                        reportStateChange(CallState.DISMISSED)
                        break
                    }
                }
            }
            closeSocket(socket)
        } ?: return
    }

    private fun connectSocket(contact: Contact): Socket? {

        var socketTimeoutException = false
        var connectException = false
        var unknownHostException = false
        var exception = false

        val socket = Socket()
        try {
            socket.connect(
                parseSocketAddress(contact.ipAddress + ":${Constant.SERVER_HTTP_PORT}"),
                connectTimeout
            )
            reportStateChange(CallState.CONNECTING)
            return socket
        } catch (e: SocketTimeoutException) {
            socketTimeoutException = true
        } catch (e: ConnectException) {
            connectException = true
        } catch (e: UnknownHostException) {
            unknownHostException = true
        } catch (e: Exception) {
            exception = true
        }
        closeSocket(socket)

        when {
            socketTimeoutException -> reportStateChange(CallState.ERROR_NO_CONNECTION)
            connectException -> reportStateChange(CallState.ERROR_CONNECT_PORT)
            unknownHostException -> reportStateChange(CallState.ERROR_UNKNOWN_HOST)
            exception -> reportStateChange(CallState.ERROR_COMMUNICATION)
            contact.ipAddress?.isEmpty() == true -> reportStateChange(CallState.ERROR_NO_ADDRESSES)
            else -> reportStateChange(CallState.ERROR_NO_NETWORK)
        }

        return null
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

    enum class CallState {
        WAITING,
        CONNECTING,
        RINGING,
        CONNECTED,
        DISMISSED,
        ENDED,
        ERROR_AUTHENTICATION,
        ERROR_DECRYPTION,
        ERROR_CONNECT_PORT,
        ERROR_UNKNOWN_HOST,
        ERROR_COMMUNICATION,
        ERROR_NO_CONNECTION,
        ERROR_NO_ADDRESSES,
        ERROR_NO_NETWORK
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

        fun createIncomingCall(binder: MainService.MainBinder, socket: Socket) {
            Thread {
                try {
                    createIncomingCallInternal(binder, socket)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //decline()
                }
            }.start()
        }

        private fun createIncomingCallInternal(binder: MainService.MainBinder, socket: Socket) {
            val pw = PacketWriter(socket)
            val pr = PacketReader(socket)

            val decline = {
                try {
                    val dismissedMessage = "{\"action\":\"dismissed\"}"
                    pw.writeMessage(dismissedMessage.toByteArray())
                    socket.close()
                } catch (e: Exception) {
                    closeSocket(socket)
                }
            }

            val request = pr.readMessage()
            if (request == null) {
                Log.d(this, "createIncomingCallInternal() connection closed")
                socket.close()
                return
            }

            val message = String((request), Charsets.UTF_8)
            val obj = JSONObject(message)
            val action = obj.optString("action", "")
            Log.d(this, "createIncomingCallInternal() ---> response : $message")
            when (action) {
                "call" -> {
                    val offer = obj.getString("offer")
                    Log.d(this, "Dior: ------> $offer");

                    incomingRTCCall?.cleanup()
                    incomingRTCCall = WebRTCClient(binder, socket, offer)

                    try {
                        Looper.prepare()
                        val messageDialog = AlertDialog.Builder(contextMain)
                        with(messageDialog) {
                            setTitle("Call Incoming")
                            setMessage("Do you agree to accept?")
                            setPositiveButton("Accept") { dialog, _ ->
                                run {
                                    val intent = Intent(contextMain, CallActivity::class.java)
                                    intent.action = CallActivity.ACTION_INCOMING_CALL
                                    contextMain?.startActivity(intent)
                                    dialog.dismiss()
                                }
                            }
                            setNegativeButton("Cancel") { dialog, _ ->
                                run {
                                    decline()
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
                "ping" -> {
                    val response = "{\"action\":\"pong\"}"
                    pw.writeMessage(response.toByteArray())
                }
                else -> {
                    Log.d(this, "createIncomingCallInternal(): ------> $action");
                }
            }
        }
    }
}