package io.micronaut.docs.server.functional

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

/**
 * An item of the documentation examples.
 *
 * @param id   The id
 * @param name The name
 */
@Introspected
data class Item(@JsonProperty("id") val id: Long, @JsonProperty("name") val name: String)
