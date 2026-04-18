package es.niceto.ubersdr.data.instances

import es.niceto.ubersdr.data.network.dto.InstanceDto

data class PublicInstance(
    val id: String,
    val label: String,
    val callsign: String?,
    val name: String?,
    val countryName: String?,
    val location: String?,
    val publicUrl: String,
    val isOnline: Boolean,
    val availableClients: Int?,
    val maxClients: Int?,
    val maxSessionTimeSeconds: Int?,
    val features: Set<String>
)

class InstanceDirectoryRepository(
    private val service: InstanceDirectoryService
) {
    suspend fun getOnlineInstances(
        endpointUrl: String = InstanceDirectoryService.DEFAULT_INSTANCES_ENDPOINT_URL
    ): List<PublicInstance> {
        return service.getOnlineInstances(endpointUrl)
            .instances
            .map { it.toPublicInstance() }
    }
}

private fun InstanceDto.toPublicInstance(): PublicInstance {
    val resolvedPublicUrl = publicUrl.trim()
    val resolvedCallsign = callsign?.trim().takeUnless { it.isNullOrEmpty() }
    val resolvedName = name?.trim().takeUnless { it.isNullOrEmpty() }

    return PublicInstance(
        id = id,
        label = resolvedCallsign ?: resolvedName ?: resolvedPublicUrl,
        callsign = resolvedCallsign,
        name = resolvedName,
        countryName = countryName?.trim().takeUnless { it.isNullOrEmpty() },
        location = location?.trim().takeUnless { it.isNullOrEmpty() },
        publicUrl = resolvedPublicUrl,
        isOnline = isOnline,
        availableClients = availableClients,
        maxClients = maxClients,
        maxSessionTimeSeconds = maxSessionTimeSeconds,
        features = buildFeatures()
    )
}

private fun InstanceDto.buildFeatures(): Set<String> {
    val features = linkedSetOf<String>()

    if (cwSkimmer) {
        features += "CW Skimmer"
    }
    if (digitalDecodes) {
        features += "Digital Decodes"
    }
    if (noiseFloor) {
        features += "Noise Floor"
    }
    if (chatEnabled) {
        features += "Chat"
    }
    if (corsEnabled) {
        features += "CORS"
    }

    addons.orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { features += it }

    return features
}
