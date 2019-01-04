package io.micronaut.runtime.event.annotation.itfce;

public class ThingCreatedEvent {

    private String thing;

    public ThingCreatedEvent(String thing) {
        this.thing = thing;
    }

    public String getThing() {
        return thing;
    }
}