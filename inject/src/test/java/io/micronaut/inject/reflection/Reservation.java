package io.micronaut.inject.reflection;

import java.util.List;

public class Reservation extends AbstractReservation implements Auditable {

    @Override
    @Tag("reservation")
    public List<@Tag("reservation-item") String> reserve(@Tag("reservation-param") String name) {
        return List.of("reservation:" + name);
    }
}
