package io.micronaut.reflection;

import io.micronaut.core.annotation.Creator;

/**
 * The instantiation routes a reflective definition selects, as the processors select them.
 */
public final class DefConstructors {

    private DefConstructors() {
    }

    /**
     * Two accessible constructors, neither annotated: the public one, not the one taking no parameter.
     */
    public static class PublicOverNoArg {

        private final Warehouse warehouse;

        public PublicOverNoArg(Warehouse warehouse) {
            this.warehouse = warehouse;
        }

        protected PublicOverNoArg() {
            this.warehouse = null;
        }

        public Warehouse getWarehouse() {
            return warehouse;
        }
    }

    /**
     * A private constructor and a static {@link Creator} factory: the factory is the instantiation route.
     */
    public static class Factory {

        private final Warehouse warehouse;

        private Factory(Warehouse warehouse) {
            this.warehouse = warehouse;
        }

        @Creator
        public static Factory of(Warehouse warehouse) {
            return new Factory(warehouse);
        }

        public Warehouse getWarehouse() {
            return warehouse;
        }
    }

    /**
     * A record declaring a secondary constructor annotated {@link Creator}: the annotated one wins over the
     * canonical one.
     */
    public record Annotated(Warehouse warehouse, String label) {

        @Creator
        public Annotated(Warehouse warehouse) {
            this(warehouse, "created");
        }
    }
}
