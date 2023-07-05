package com.example.simple_webrtc.model

import java.io.Serializable

data class Contact(
    val name: String? = null,
    val ipAddress: String? = null
) : Serializable
