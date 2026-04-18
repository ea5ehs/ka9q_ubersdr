package es.niceto.ubersdr.data.network.dto

import com.squareup.moshi.Json

data class InstanceDirectoryResponseDto(
    val count: Int = 0,
    val instances: List<InstanceDto> = emptyList()
)

data class InstanceDto(
    val id: String = "",
    val callsign: String? = null,
    val name: String? = null,
    @Json(name = "country_name")
    val countryName: String? = null,
    val location: String? = null,
    @Json(name = "public_url")
    val publicUrl: String = "",
    @Json(name = "is_online")
    val isOnline: Boolean = false,
    @Json(name = "available_clients")
    val availableClients: Int? = null,
    @Json(name = "max_clients")
    val maxClients: Int? = null,
    @Json(name = "max_session_time")
    val maxSessionTimeSeconds: Int? = null,
    @Json(name = "cw_skimmer")
    val cwSkimmer: Boolean = false,
    @Json(name = "digital_decodes")
    val digitalDecodes: Boolean = false,
    @Json(name = "noise_floor")
    val noiseFloor: Boolean = false,
    @Json(name = "chat_enabled")
    val chatEnabled: Boolean = false,
    @Json(name = "cors_enabled")
    val corsEnabled: Boolean = false,
    val addons: List<String>? = null
)
