package es.niceto.ubersdr.data.network

import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.data.network.dto.ConnectionRequestDto
import es.niceto.ubersdr.data.network.dto.ConnectionResponseDto
import es.niceto.ubersdr.data.network.dto.DescriptionResponseDto

class ConnectionService(
    private val api: ConnectionApi
) {
    suspend fun postConnection(userSessionId: String, password: String? = null): ConnectionResponseDto {
        return api.postConnection(
            ConnectionRequestDto(
                userSessionId = userSessionId,
                password = password
            )
        )
    }

    suspend fun getDescription(): DescriptionResponseDto = api.getDescription()

    suspend fun getBands(): List<BandDto> = api.getBands()
}
