package io.micronaut.inject.provider;

import org.atinject.tck.auto.Drivers;
import org.atinject.tck.auto.Seat;
import org.atinject.tck.auto.Tire;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Seats {
    Provider<Seat> driversSeatProvider;
    Provider<Tire> spareTireProvider;

    Seats(@Drivers Provider<Seat> driversSeatProvider, @Named("spare") Provider<Tire> spareTireProvider) {
        this.driversSeatProvider = driversSeatProvider;
        this.spareTireProvider = spareTireProvider;
    }
}