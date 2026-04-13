package es.niceto.ubersdr.data.network

import es.niceto.ubersdr.data.network.dto.ConnectionRequestDto
import es.niceto.ubersdr.data.network.dto.ConnectionResponseDto
import es.niceto.ubersdr.data.network.dto.DescriptionResponseDto
import es.niceto.ubersdr.data.network.dto.BandDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ConnectionApi {
    @POST("connection")
    suspend fun postConnection(@Body request: ConnectionRequestDto): ConnectionResponseDto

    @GET("api/description")
    suspend fun getDescription(): DescriptionResponseDto

    @GET("api/bands")
    suspend fun getBands(): List<BandDto>
}
