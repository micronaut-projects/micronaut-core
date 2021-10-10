package io.micronaut.docs.basics

// tag::imports[]

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

// end::imports[]

// tag::class[]
class Message @JsonCreator
constructor(@param:JsonProperty("text") val text: String)
// end::class[]
