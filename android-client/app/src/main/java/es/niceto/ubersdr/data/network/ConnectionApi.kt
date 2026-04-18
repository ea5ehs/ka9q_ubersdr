package es.niceto.ubersdr.data.network

import es.niceto.ubersdr.data.network.dto.ConnectionRequestDto
import es.niceto.ubersdr.data.network.dto.ConnectionResponseDto
import es.niceto.ubersdr.data.network.dto.DescriptionResponseDto
import es.niceto.ubersdr.data.network.dto.BandDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface ConnectionApi {
    @POST
    suspend fun postConnection(
        @Url url: String,
        @Body request: ConnectionRequestDto
    ): ConnectionResponseDto

    @GET
    suspend fun getDescription(@Url url: String): DescriptionResponseDto

    @GET
    suspend fun getBands(@Url url: String): List<BandDto>
}
