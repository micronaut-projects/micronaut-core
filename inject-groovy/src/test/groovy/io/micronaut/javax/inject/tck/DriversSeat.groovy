package io.micronaut.javax.inject.tck

import io.micronaut.javax.inject.tck.accessories.Cupholder
import io.micronaut.javax.inject.tck.accessories.Cupholder

import javax.inject.Inject

class DriversSeat extends Seat {

    @Inject
    DriversSeat(Cupholder cupholder) {
        super(cupholder)
    }
}
