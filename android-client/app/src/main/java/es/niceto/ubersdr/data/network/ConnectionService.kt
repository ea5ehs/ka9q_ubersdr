package es.niceto.ubersdr.data.network

import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.data.network.dto.ConnectionRequestDto
import es.niceto.ubersdr.data.network.dto.ConnectionResponseDto
import es.niceto.ubersdr.data.network.dto.DescriptionResponseDto

class ConnectionService(
    private val api: ConnectionApi
) {
    suspend fun postConnection(
        baseUrl: String,
        userSessionId: String,
        password: String? = null
    ): ConnectionResponseDto {
        return api.postConnection(
            url = buildApiUrl(baseUrl, "connection"),
            request = ConnectionRequestDto(
                userSessionId = userSessionId,
                password = password
            )
        )
    }

    suspend fun getDescription(baseUrl: String): DescriptionResponseDto =
        api.getDescription(buildApiUrl(baseUrl, "api/description"))

    suspend fun getBands(baseUrl: String): List<BandDto> =
        api.getBands(buildApiUrl(baseUrl, "api/bands"))

    private fun buildApiUrl(baseUrl: String, path: String): String {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) {
            baseUrl
        } else {
            "$baseUrl/"
        }
        return normalizedBaseUrl + path
    }
}
