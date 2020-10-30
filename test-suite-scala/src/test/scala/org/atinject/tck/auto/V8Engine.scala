package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.SpareTire
import javax.inject.Inject
import javax.inject.Named

class V8Engine() extends GasEngine {
  publicNoArgsConstructorInjected = true

  @Inject override private[auto] def injectPackagePrivateMethod(): Unit = {
    if (subPackagePrivateMethodInjected) overriddenPackagePrivateMethodInjectedTwice = true
    subPackagePrivateMethodInjected = true
  }

  /**
   * Qualifiers are swapped from how they appear in the superclass.
   */
  override def injectQualifiers(seatA: Seat, @Drivers seatB: Seat, tireA: Tire, @Named("spare") tireB: Tire): Unit = {
    if (seatA.isInstanceOf[DriversSeat] || !seatB.isInstanceOf[DriversSeat] || tireA.isInstanceOf[SpareTire] || !tireB.isInstanceOf[SpareTire]) qualifiersInheritedFromOverriddenMethod = true
  }

  override private[auto] def injectPackagePrivateMethodForOverride(): Unit = {
    subPackagePrivateMethodForOverrideInjected = true
  }

  @Inject override def injectTwiceOverriddenWithOmissionInMiddle(): Unit = {
    overriddenTwiceWithOmissionInMiddleInjected = true
  }

  override def injectTwiceOverriddenWithOmissionInSubclass(): Unit = {
    overriddenTwiceWithOmissionInSubclassInjected = true
  }
}