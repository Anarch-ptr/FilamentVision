package com.filamentvision.input

import com.filamentvision.model.ConnectionProfile

data class CreatedImageInput(
    val source: ImageInputSource,
    val endpointRuntimeCount: Int,
)

class ImageInputSourceFactory {
    fun create(profile: ConnectionProfile): CreatedImageInput {
        val referencedIds = profile.cameras.map { it.endpointId }.filter(String::isNotBlank).toSet()
        val endpoints = profile.endpoints.filter { it.endpointId in referencedIds }.distinctBy { it.endpointId }
        if (endpoints.isEmpty()) return CreatedImageInput(UnavailableImageInputSource.unconfigured(), 0)
        return CreatedImageInput(
            source = UnavailableImageInputSource.missingDrivers(endpoints.map { it.protocol }.toSet()),
            endpointRuntimeCount = endpoints.size,
        )
    }
}

