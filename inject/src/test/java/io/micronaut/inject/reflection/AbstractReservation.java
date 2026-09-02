package io.micronaut.inject.reflection;

import java.util.List;

public abstract class AbstractReservation implements Reservable {

    @Override
    @Tag("abstract")
    public List<@Tag("abstract-item") String> reserve(@Tag("abstract-param") String name) {
        return List.of("abstract:" + name);
    }
}
