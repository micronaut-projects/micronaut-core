package org.atinject.tck.auto.accessories;

import org.atinject.tck.auto.Seat;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Cupholder {

    public final Provider<Seat> seatProvider;

    @Inject
    public Cupholder(Provider<Seat> seatProvider) {
        this.seatProvider = seatProvider;
    }
}
