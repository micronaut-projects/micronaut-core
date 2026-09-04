package io.micronaut.reflection;

/**
 * A class carrying a repeated annotation, so that the container itself can be read.
 */
@Tag("ledger")
@Tag("book")
public class Tagged implements Comparable<Tagged> {

    @Override
    public int compareTo(Tagged other) {
        return 0;
    }
}
