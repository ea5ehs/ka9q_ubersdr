package es.niceto.ubersdr.data.network.dto

import com.squareup.moshi.Json

data class ConnectionRequestDto(
    @param:Json(name = "user_session_id")
    val userSessionId: String,
    val password: String? = null
)

data class ConnectionResponseDto(
    @Json(name = "client_ip")
    val clientIp: String = "",
    val allowed: Boolean = false,
    val reason: String? = null,
    @Json(name = "session_timeout")
    val sessionTimeout: Int = 0,
    @Json(name = "max_session_time")
    val maxSessionTime: Int = 0,
    val bypassed: Boolean = false,
    @Json(name = "allowed_iq_modes")
    val allowedIqModes: List<String> = emptyList()
)

data class DescriptionResponseDto(
    @Json(name = "default_frequency")
    val defaultFrequency: Long = 14_175_000L,
    @Json(name = "default_mode")
    val defaultMode: String = "usb"
)

data class BandDto(
    val label: String = "",
    val start: Long = 0L,
    val end: Long = 0L,
    val mode: String? = null
)
