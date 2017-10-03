package org.atinject.tck.auto;

import org.atinject.tck.auto.accessories.Cupholder;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Seat {

    private final Cupholder cupholder;

    @Inject
    Seat(Cupholder cupholder) {
        this.cupholder = cupholder;
    }

    public Cupholder getCupholder() {
        return cupholder;
    }
}
