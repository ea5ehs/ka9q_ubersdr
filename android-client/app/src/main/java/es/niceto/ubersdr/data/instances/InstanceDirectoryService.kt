package es.niceto.ubersdr.data.instances

import es.niceto.ubersdr.data.network.ConnectionApi
import es.niceto.ubersdr.data.network.dto.InstanceDirectoryResponseDto

class InstanceDirectoryService(
    private val api: ConnectionApi
) {
    suspend fun getOnlineInstances(
        endpointUrl: String = DEFAULT_INSTANCES_ENDPOINT_URL
    ): InstanceDirectoryResponseDto = api.getInstances(endpointUrl)

    companion object {
        const val DEFAULT_INSTANCES_ENDPOINT_URL =
            "https://instances.ubersdr.org/api/instances?online_only=true"

        const val FALLBACK_INSTANCES_ENDPOINT_URL =
            "https://instances.ubersdr.org/api/instances?conditions=true&online_only=true"
    }
}
