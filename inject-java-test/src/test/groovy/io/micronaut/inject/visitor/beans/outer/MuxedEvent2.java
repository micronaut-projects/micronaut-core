package io.micronaut.inject.visitor.beans.outer;

public class MuxedEvent2 {
    String compartment;
    String content;

    public MuxedEvent2(String compartment, String content) {
        this.compartment = compartment;
        this.content = content;
    }

    public MuxedEvent2() {
    }
}
