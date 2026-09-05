package io.micronaut.reflection;

import java.util.ArrayList;
import java.util.List;

/**
 * A bean whose life cycle methods are named rather than annotated, as another container names them.
 */
public class Named {

    private final List<String> events = new ArrayList<>();

    public List<String> getEvents() {
        return events;
    }

    public void open() {
        events.add("open");
    }

    public void close() {
        events.add("close");
    }
}
