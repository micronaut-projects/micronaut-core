package io.micronaut.inject.visitor.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MuxedEvent1 {
    @JsonProperty
    String compartment;
    @JsonProperty
    String content;

    public MuxedEvent1(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent1() {
    }
}
