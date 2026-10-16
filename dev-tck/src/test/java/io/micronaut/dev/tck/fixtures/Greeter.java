package io.micronaut.dev.tck.fixtures;

/**
 * The parent-tier contract the reloadable fixture implements, as a module's own API would.
 */
public interface Greeter {
    String greet();
}
