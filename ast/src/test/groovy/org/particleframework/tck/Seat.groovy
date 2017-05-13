package org.particleframework.tck

import org.particleframework.tck.accessories.Cupholder

/**
 * Created by graemerocher on 12/05/2017.
 */
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Seat {

    private final Cupholder cupholder

    @Inject
    Seat(Cupholder cupholder) {
        this.cupholder = cupholder
    }

    Cupholder getCupholder() {
        return cupholder
    }
}
