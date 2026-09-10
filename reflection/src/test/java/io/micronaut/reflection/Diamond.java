package io.micronaut.reflection;

import java.util.List;

/**
 * Two branches reaching the same interface: {@link Reservable} is a super interface of both {@link Left} and
 * {@link Right}, and the walk reports its declaration once.
 */
public interface Diamond {

    interface Left extends Reservable {
    }

    interface Right extends Reservable {
    }

    class Both implements Left, Right {

        @Override
        @Tag("both")
        public List<String> reserve(String name) {
            return List.of("both:" + name);
        }
    }
}
