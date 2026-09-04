package io.micronaut.reflection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The injection points a generic super class declares, read through the type that gives its variable a value.
 */
public final class DefGenerics {

    private DefGenerics() {
    }

    /**
     * A generic super class whose field and setter are declared over its variable.
     *
     * @param <T> The injected type
     */
    public static class Base<T> {

        @Inject
        T dep;

        private T service;

        @Inject
        public void setService(T service) {
            this.service = service;
        }

        public T getDep() {
            return dep;
        }

        public T getService() {
            return service;
        }
    }

    /**
     * A bean resolving the variable of its direct super class.
     */
    @Singleton
    public static class Impl extends Base<Warehouse> {
    }

    /**
     * A generic class passing its variable on to its own super class.
     *
     * @param <T> The injected type
     */
    public static class Middle<T> extends Base<T> {
    }

    /**
     * A bean resolving the variable two levels up.
     */
    @Singleton
    public static class TwoLevel extends Middle<Warehouse> {
    }

    /**
     * A generic super class declaring a collection injection point.
     *
     * @param <T> The element type
     */
    public static class Collected<T> {

        private List<T> all = List.of();

        @Inject
        public void setAll(List<T> all) {
            this.all = all;
        }

        public List<T> getAll() {
            return all;
        }
    }

    /**
     * A bean resolving the element of the inherited collection.
     */
    @Singleton
    public static class AllCouriers extends Collected<Courier> {
    }
}
