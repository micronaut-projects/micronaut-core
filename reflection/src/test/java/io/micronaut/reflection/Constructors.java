package io.micronaut.reflection;

import io.micronaut.core.annotation.Creator;

/**
 * The four rules a reflective introspection selects a constructor by.
 *
 * <p>Every constructor here has an empty body and takes parameters it does not use: what the specs read is
 * which constructor the introspection picks and what arguments it reports, so the declarations are the whole
 * fixture and running one has nothing to do.</p>
 */
public final class Constructors {

    private Constructors() {
        // the fixture is only ever described, never instantiated
    }

    /**
     * A {@link Creator} wins, even on a constructor that is not public.
     */
    public static class Annotated {
        public Annotated() {
            // empty on purpose - only the declaration is read
        }

        public Annotated(String only) { // NOSONAR - the parameter is unused on purpose, it is what is described
            // empty on purpose - only the declaration is read
        }

        @Creator
        Annotated(String first, int second) { // NOSONAR - the parameters are unused on purpose, they are what is described
            // empty on purpose - only the declaration is read
        }
    }

    /**
     * With no {@link Creator}, the only public constructor.
     */
    public static class OnlyPublic {
        OnlyPublic(String only) { // NOSONAR - the parameter is unused on purpose, it is what is described
            // empty on purpose - only the declaration is read
        }

        public OnlyPublic(String first, int second) { // NOSONAR - the parameters are unused on purpose, they are what is described
            // empty on purpose - only the declaration is read
        }
    }

    /**
     * Among several public ones, the one taking no parameter.
     */
    public static class NoArgAmongMany {
        public NoArgAmongMany(String only) { // NOSONAR - the parameter is unused on purpose, it is what is described
            // empty on purpose - only the declaration is read
        }

        public NoArgAmongMany(String first, int second) { // NOSONAR - the parameters are unused on purpose, they are what is described
            // empty on purpose - only the declaration is read
        }

        public NoArgAmongMany() {
            // empty on purpose - only the declaration is read
        }
    }

    /**
     * With none public, the declared one taking the most parameters.
     */
    public static class NonePublic {
        NonePublic(String only) { // NOSONAR - the parameter is unused on purpose, it is what is described
            // empty on purpose - only the declaration is read
        }

        private NonePublic(String first, int second) { // NOSONAR - never called on purpose, the introspection selects it as the declared constructor with the most parameters
            // empty on purpose - only the declaration is read
        }
    }
}
