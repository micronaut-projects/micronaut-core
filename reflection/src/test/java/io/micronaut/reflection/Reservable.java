package io.micronaut.reflection;

import java.util.List;

/**
 * The root of the method hierarchy the {@code MethodHierarchySpec} walks.
 */
public interface Reservable {

    @Tag("reservable")
    List<@Tag("reservable-item") String> reserve(@Tag("reservable-param") String name);
}
