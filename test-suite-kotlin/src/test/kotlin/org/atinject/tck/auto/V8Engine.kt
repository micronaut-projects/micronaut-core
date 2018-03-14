package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

class V8Engine : GasEngine() {
    init {
        publicNoArgsConstructorInjected = true
    }

    @Inject override fun injectPackagePrivateMethod() {
        if (subPackagePrivateMethodInjected) {
            overriddenPackagePrivateMethodInjectedTwice = true
        }
        subPackagePrivateMethodInjected = true
    }

    /**
     * Qualifiers are swapped from how they appear in the superclass.
     */
    override fun injectQualifiers(seatA: Seat, @Drivers seatB: Seat,
                                  tireA: Tire, @Named("spare") tireB: Tire) {
        if (seatA is DriversSeat
                || seatB !is DriversSeat
                || tireA is SpareTire
                || tireB !is SpareTire) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    override fun injectPackagePrivateMethodForOverride() {
        subPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    override fun injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    override fun injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
