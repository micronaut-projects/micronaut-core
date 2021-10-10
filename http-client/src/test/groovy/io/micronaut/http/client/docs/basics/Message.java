package io.micronaut.http.client.docs.basics;

// tag::imports[]

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
// end::imports[]

// tag::class[]
public class Message {
    private final String text;

    @JsonCreator
    public Message(@JsonProperty("text") String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
// end::class[]
