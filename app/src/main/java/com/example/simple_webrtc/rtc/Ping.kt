package com.example.simple_webrtc.rtc

import com.example.simple_webrtc.MainService
import com.example.simple_webrtc.SocketService
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.utils.*
import com.example.simple_webrtc.utils.Utils.parseSocketAddress
import org.json.JSONObject
import java.net.ConnectException
import java.net.Socket

class Ping(private val binder: MainService.MainBinder, private val contacts: List<Contact>) : Runnable {

    private fun pingContact(contact: Contact) : Contact.State {
        Log.d(this, "pingContact() contact: ${contact.name}")
        var connected = false
        val socket = Socket()

        try {
            socket.connect(parseSocketAddress(contact.ipAddress + ":${Constant.SERVER_HTTP_PORT}"), SocketService.connectTimeout)
            if (socket.isConnected.not()) {
                return Contact.State.CONTACT_OFFLINE
            }
            socket.soTimeout = 3000
            val pw = PacketWriter(socket)
            val pr = PacketReader(socket)
            val request = "{\"action\":\"ping\"}"
            pw.writeMessage(request.toByteArray())

            val response = pr.readMessage() ?: return Contact.State.COMMUNICATION_FAILED

            val obj = JSONObject(String((response), Charsets.UTF_8))
            val action = obj.optString("action", "")
            return if (action == "pong") {
                Contact.State.CONTACT_ONLINE
            } else {
                Contact.State.COMMUNICATION_FAILED
            }

        } catch (e: ConnectException) {
            if (" ENETUNREACH " in e.toString()) {
                return Contact.State.NETWORK_UNREACHABLE
            } else {
                // target online, but App not running
                return Contact.State.APP_NOT_RUNNING
            }
        } catch (e: Exception) {
            return Contact.State.COMMUNICATION_FAILED
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    override fun run() {
        Log.d(this, "Ping has been running ")
        for (contact in contacts) {
            val state = pingContact(contact)
            Log.d(this, "contact state is $state")
            binder.getContactList().first { it.ipAddress == contact.ipAddress }.state = state
        }

        MainService.refreshContacts(binder.getService())
    }
}
