package io.micronaut.reflection;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * An overridden generic member: the override and the bridge the compiler generates for it are one injection
 * point, not two.
 */
public final class DefOverrides {

    private DefOverrides() {
    }

    /**
     * A generic super class declaring an injected setter and a life cycle method over its variable.
     *
     * @param <T> The injected type
     */
    public static class Base<T> {

        private final List<String> events = new ArrayList<>();

        @Inject
        public void setService(T service) {
            events.add("setService:" + service.getClass().getSimpleName());
        }

        @PostConstruct
        public void init(T service) {
            events.add("init:" + service.getClass().getSimpleName());
        }

        public List<String> getEvents() {
            return events;
        }
    }

    /**
     * A bean overriding both, so that the compiler generates a bridge for each.
     */
    @Singleton
    public static class Impl extends Base<Warehouse> {

        @Override
        @Inject
        public void setService(Warehouse service) {
            super.setService(service);
        }

        @Override
        @PostConstruct
        public void init(Warehouse service) {
            super.init(service);
        }
    }
}
