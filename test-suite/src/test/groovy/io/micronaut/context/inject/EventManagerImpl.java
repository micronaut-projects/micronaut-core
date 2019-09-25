package io.micronaut.context.inject;

public class EventManagerImpl implements EventManager {
    private String serviceName;

    public EventManagerImpl(String serviceName) {
        this.serviceName = serviceName;
    }

    public void register(String id) {

    }

}
