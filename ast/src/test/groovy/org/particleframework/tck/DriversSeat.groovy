package org.particleframework.tck

import org.particleframework.tck.accessories.Cupholder

import javax.inject.Inject

class DriversSeat extends Seat {

    @Inject
    DriversSeat(Cupholder cupholder) {
        super(cupholder)
    }
}
