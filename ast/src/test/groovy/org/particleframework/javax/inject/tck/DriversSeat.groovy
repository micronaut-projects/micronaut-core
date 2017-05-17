package org.particleframework.javax.inject.tck

import org.particleframework.javax.inject.tck.accessories.Cupholder

import javax.inject.Inject

class DriversSeat extends Seat {

    @Inject
    DriversSeat(Cupholder cupholder) {
        super(cupholder)
    }
}
