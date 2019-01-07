package io.micronaut.runtime.event.annotation.itfce;

import io.micronaut.context.event.ApplicationEventPublisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class DefaultThingService implements ThingCreatedEventListener {

    private List<String> things = new ArrayList<>();

    private ApplicationEventPublisher publisher;

    @Inject
    public DefaultThingService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void create(String thing) {
        publisher.publishEvent(new ThingCreatedEvent(thing));
    }

    @Override
    public void onThingCreated(ThingCreatedEvent event) {
        String thing = event.getThing();
        things.add(thing);
    }

    public List<String> getThings() {
        return things;
    }
}
