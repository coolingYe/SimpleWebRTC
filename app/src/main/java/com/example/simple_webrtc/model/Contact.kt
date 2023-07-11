package com.example.simple_webrtc.model

import java.io.Serializable

data class Contact(
    val name: String? = null,
    val ipAddress: String? = null
) : Serializable {

    enum class State {
        CONTACT_ONLINE,
        CONTACT_OFFLINE,
        NETWORK_UNREACHABLE,
        APP_NOT_RUNNING, // host is online, but Meshenger does not run
        AUTHENTICATION_FAILED, // authentication failed, key might have changed
        COMMUNICATION_FAILED, // something went wrong during communication
    }

    var state = State.CONTACT_OFFLINE
}
