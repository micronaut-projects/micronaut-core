package io.micronaut.inject.reflection;

import io.micronaut.core.annotation.Creator;

/**
 * The four rules a reflective introspection selects a constructor by.
 */
public final class Constructors {

    private Constructors() {
    }

    /**
     * A {@link Creator} wins, even on a constructor that is not public.
     */
    public static class Annotated {
        public Annotated() {
        }

        public Annotated(String only) {
        }

        @Creator
        Annotated(String first, int second) {
        }
    }

    /**
     * With no {@link Creator}, the only public constructor.
     */
    public static class OnlyPublic {
        OnlyPublic(String only) {
        }

        public OnlyPublic(String first, int second) {
        }
    }

    /**
     * Among several public ones, the one taking no parameter.
     */
    public static class NoArgAmongMany {
        public NoArgAmongMany(String only) {
        }

        public NoArgAmongMany(String first, int second) {
        }

        public NoArgAmongMany() {
        }
    }

    /**
     * With none public, the declared one taking the most parameters.
     */
    public static class NonePublic {
        NonePublic(String only) {
        }

        private NonePublic(String first, int second) {
        }
    }
}
