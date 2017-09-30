package org.particleframework.javax.inject.tck

import groovy.transform.PackageScope
import org.particleframework.javax.inject.tck.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

abstract class Engine {

    protected boolean publicNoArgsConstructorInjected
    protected boolean subPackagePrivateMethodInjected
    protected boolean superPackagePrivateMethodInjected
    protected boolean subPackagePrivateMethodForOverrideInjected
    protected boolean superPackagePrivateMethodForOverrideInjected

    protected boolean overriddenTwiceWithOmissionInMiddleInjected
    protected boolean overriddenTwiceWithOmissionInSubclassInjected

    protected Seat seatA
    protected Seat seatB
    protected Tire tireA
    protected Tire tireB

    public boolean overriddenPackagePrivateMethodInjectedTwice
    public boolean qualifiersInheritedFromOverriddenMethod

    @PackageScope @Inject void injectPackagePrivateMethod() {
        superPackagePrivateMethodInjected = true
    }

    @PackageScope @Inject void injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    void injectQualifiers(@Drivers Seat seatA, Seat seatB,
                          @Named("spare") Tire tireA, Tire tireB) {
        if (!(seatA instanceof DriversSeat)
                || (seatB instanceof DriversSeat)
                || !(tireA instanceof SpareTire)
                || (tireB instanceof SpareTire)) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    @Inject
    void injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    @Inject
    void injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
