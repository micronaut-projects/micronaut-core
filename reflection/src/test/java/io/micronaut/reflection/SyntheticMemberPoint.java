package io.micronaut.reflection;

/**
 * A record whose component is both a field and an accessor, read reflectively.
 *
 * @param x A component
 */
public record SyntheticMemberPoint(int x) {
}
