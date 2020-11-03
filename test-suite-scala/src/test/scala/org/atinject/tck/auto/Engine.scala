package org.atinject.tck.auto

import javax.inject.Inject
import javax.inject.Named
import org.atinject.tck.auto.accessories.SpareTire

abstract class Engine {
  private[auto] var publicNoArgsConstructorInjected = false
  protected var subPackagePrivateMethodInjected = false
  protected var superPackagePrivateMethodInjected = false
  private[auto] var subPackagePrivateMethodForOverrideInjected = false
  private[auto] var superPackagePrivateMethodForOverrideInjected = false
  private[auto] var overriddenTwiceWithOmissionInMiddleInjected = false
  private[auto] var overriddenTwiceWithOmissionInSubclassInjected = false
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