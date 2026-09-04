package io.micronaut.reflection;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A bean with life cycle methods the processors never saw.
 */
@Singleton
public class Lifecycle {

    private final List<String> events = new ArrayList<>();

    public List<String> getEvents() {
        return events;
    }

    @PostConstruct
    void start() {
        events.add("start");
    }

    @PreDestroy
    void stop() {
        events.add("stop");
    }
}
