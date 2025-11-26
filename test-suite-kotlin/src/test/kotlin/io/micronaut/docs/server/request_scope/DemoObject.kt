package io.micronaut.docs.server.request_scope

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
data class DemoObject(
    @JsonProperty("text") val text: String,
    @JsonProperty("list") val list: List<String>,
)
