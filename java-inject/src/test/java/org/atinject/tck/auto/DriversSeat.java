package org.atinject.tck.auto;

import org.atinject.tck.auto.accessories.Cupholder;

import javax.inject.Inject;

public class DriversSeat extends Seat {

    @Inject
    public DriversSeat(Cupholder cupholder) {
        super(cupholder);
    }
}
