package org.particleframework.javax.inject.tck

import groovy.transform.PackageScope
import org.particleframework.javax.inject.tck.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

class V8Engine extends GasEngine {

    V8Engine() {
        publicNoArgsConstructorInjected = true
    }

    @Inject @PackageScope void injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            overriddenPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    /**
     * Qualifiers are swapped from how they appear in the superclass.
     */
    void injectQualifiers(Seat seatA, @Drivers Seat seatB,
                                 Tire tireA, @Named("spare") Tire tireB) {
        if ((seatA instanceof DriversSeat)
                || !(seatB instanceof DriversSeat)
                || (tireA instanceof SpareTire)
                || !(tireB instanceof SpareTire)) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    @PackageScope void injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    void injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    void injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
