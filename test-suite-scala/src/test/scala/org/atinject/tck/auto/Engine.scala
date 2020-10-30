package org.atinject.tck.auto

import org.atinject.tck.auto.accessories.SpareTire
import javax.inject.Inject
import javax.inject.Named

abstract class Engine {
  protected var publicNoArgsConstructorInjected = false
  protected var subPackagePrivateMethodInjected = false
  protected var superPackagePrivateMethodInjected = false
  protected var subPackagePrivateMethodForOverrideInjected = false
  protected var superPackagePrivateMethodForOverrideInjected = false
  protected var overriddenTwiceWithOmissionInMiddleInjected = false
  protected var overriddenTwiceWithOmissionInSubclassInjected = false
  protected var seatA: Seat = null
  protected var seatB: Seat = null
  protected var tireA: Tire = null
  protected var tireB: Tire = null
  var overriddenPackagePrivateMethodInjectedTwice = false
  var qualifiersInheritedFromOverriddenMethod = false

  @Inject private[auto] def injectPackagePrivateMethod(): Unit = {
    superPackagePrivateMethodInjected = true
  }

  @Inject private[auto] def injectPackagePrivateMethodForOverride(): Unit = {
    superPackagePrivateMethodForOverrideInjected = true
  }

  @Inject def injectQualifiers(@Drivers seatA: Seat, seatB: Seat, @Named("spare") tireA: Tire, tireB: Tire): Unit = {
    if (!seatA.isInstanceOf[DriversSeat] || seatB.isInstanceOf[DriversSeat] || !tireA.isInstanceOf[SpareTire] || tireB.isInstanceOf[SpareTire]) qualifiersInheritedFromOverriddenMethod = true
  }

  @Inject def injectTwiceOverriddenWithOmissionInMiddle(): Unit = {
    overriddenTwiceWithOmissionInMiddleInjected = true
  }

  @Inject def injectTwiceOverriddenWithOmissionInSubclass(): Unit = {
    overriddenTwiceWithOmissionInSubclassInjected = true
  }
}