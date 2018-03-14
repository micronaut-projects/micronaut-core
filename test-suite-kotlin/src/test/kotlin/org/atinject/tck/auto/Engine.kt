package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.SpareTire

import javax.inject.Inject
import javax.inject.Named

abstract class Engine {

    var publicNoArgsConstructorInjected: Boolean = false
    var subPackagePrivateMethodInjected: Boolean = false
    var superPackagePrivateMethodInjected: Boolean = false
    var subPackagePrivateMethodForOverrideInjected: Boolean = false
    var superPackagePrivateMethodForOverrideInjected: Boolean = false

    var overriddenTwiceWithOmissionInMiddleInjected: Boolean = false
    var overriddenTwiceWithOmissionInSubclassInjected: Boolean = false

    protected var seatA: Seat? = null
    protected var seatB: Seat? = null
    protected var tireA: Tire? = null
    protected var tireB: Tire? = null

    var overriddenPackagePrivateMethodInjectedTwice: Boolean = false
    var qualifiersInheritedFromOverriddenMethod: Boolean = false

    @Inject internal open fun injectPackagePrivateMethod() {
        superPackagePrivateMethodInjected = true
    }

    @Inject internal open fun injectPackagePrivateMethodForOverride() {
        superPackagePrivateMethodForOverrideInjected = true
    }

    @Inject
    open fun injectQualifiers(@Drivers seatA: Seat, seatB: Seat,
                              @Named("spare") tireA: Tire, tireB: Tire) {
        if (seatA !is DriversSeat
                || seatB is DriversSeat
                || tireA !is SpareTire
                || tireB is SpareTire) {
            qualifiersInheritedFromOverriddenMethod = true
        }
    }

    @Inject
    open fun injectTwiceOverriddenWithOmissionInMiddle() {
        overriddenTwiceWithOmissionInMiddleInjected = true
    }

    @Inject
    open fun injectTwiceOverriddenWithOmissionInSubclass() {
        overriddenTwiceWithOmissionInSubclassInjected = true
    }
}
