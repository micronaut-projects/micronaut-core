package io.micronaut.reflection;

/**
 * A record whose component is both a field and an accessor, read reflectively, with a getter of no component.
 *
 * @param x A component
 */
public record SyntheticMemberPoint(int x) {

    /**
     * @return A value of no component
     */
    public int getDoubled() {
        return x * 2;
    }
}
