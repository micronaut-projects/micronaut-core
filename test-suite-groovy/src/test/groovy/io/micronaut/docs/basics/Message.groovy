package io.micronaut.docs.basics

// tag::imports[]

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
// end::imports[]


// tag::class[]
class Message {
    private final String text

    @JsonCreator
    Message(@JsonProperty("text") String text) {
        this.text = text
    }

    String getText() {
        return text
    }
}
// end::class[]
