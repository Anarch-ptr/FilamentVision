package com.filamentvision.input

import com.filamentvision.model.EndpointProfile
import com.filamentvision.model.TransportProtocol

fun interface TransportDriverFactory {
    fun create(endpoint: EndpointProfile): TransportAdapter
}

class TransportDriverRegistry(
    private val factories: Map<TransportProtocol, TransportDriverFactory> = emptyMap(),
) {
    fun supports(protocol: TransportProtocol): Boolean = protocol in factories

    fun create(endpoint: EndpointProfile): TransportAdapter? = factories[endpoint.protocol]?.create(endpoint)
}

